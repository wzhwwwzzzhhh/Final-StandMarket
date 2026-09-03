-- B6 RabbitMQ reliability migration for MySQL 8.
-- Forward-only: exact, empty prefixes may continue; unknown/dirty/partial shapes stop.

DROP PROCEDURE IF EXISTS migrate_b6_seckill_mq_reliability;
DELIMITER $$
CREATE PROCEDURE migrate_b6_seckill_mq_reliability()
BEGIN
    DECLARE object_count INT DEFAULT 0;
    DECLARE row_count_value BIGINT DEFAULT 0;
    DECLARE message_exists INT DEFAULT 0;
    DECLARE compensation_exists INT DEFAULT 0;
    DECLARE anomaly_exists INT DEFAULT 0;
    DECLARE index_columns VARCHAR(2048);
    DECLARE column_signature TEXT;
    DECLARE order_number_charset VARCHAR(64);
    DECLARE order_number_collation VARCHAR(64);
    DECLARE order_number_nullable VARCHAR(3);
    DECLARE order_number_comment_literal TEXT;

    SET SESSION group_concat_max_len=8192;

    SELECT COUNT(*) INTO object_count FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='seckill_order';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B6 migration requires seckill_order';
    END IF;

    SELECT COUNT(*) INTO object_count FROM seckill_order
    WHERE order_number IS NULL OR TRIM(order_number)=''
       OR order_number NOT REGEXP '^[0-9]{1,50}$';
    IF object_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_order contains invalid order_number';
    END IF;
    SELECT COUNT(*) INTO object_count FROM
      (SELECT order_number FROM seckill_order GROUP BY order_number HAVING COUNT(*) > 1) duplicates;
    IF object_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_order contains duplicate order_number';
    END IF;

    SELECT COUNT(*),MAX(character_set_name),MAX(collation_name),MAX(is_nullable),MAX(QUOTE(column_comment))
      INTO object_count,order_number_charset,order_number_collation,order_number_nullable,
           order_number_comment_literal
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='seckill_order' AND column_name='order_number'
      AND data_type='varchar' AND column_type='varchar(50)'
      AND character_set_name IS NOT NULL AND collation_name IS NOT NULL
      AND column_default IS NULL AND extra='';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_order.order_number definition mismatch';
    END IF;
    IF order_number_charset NOT REGEXP '^[a-z0-9_]+$'
       OR order_number_collation NOT REGEXP '^[a-z0-9_]+$'
       OR order_number_collation NOT LIKE CONCAT(order_number_charset,'\\_%')
       OR order_number_nullable NOT IN ('YES','NO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_order.order_number charset/collation mismatch';
    END IF;
    SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name,':',
           COALESCE(CAST(sub_part AS CHAR),'FULL'),':',index_type,':',COALESCE(collation,'NONE'),':',is_visible)
           ORDER BY seq_in_index) INTO index_columns
    FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='seckill_order'
      AND index_name='idx_seckill_order_number' AND non_unique=0;
    IF index_columns IS NULL OR index_columns <> 'idx_seckill_order_number:0:1:order_number:FULL:BTREE:A:YES' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='idx_seckill_order_number definition mismatch';
    END IF;
    SELECT COUNT(*) INTO message_exists FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='seckill_message_log';
    SELECT COUNT(*) INTO compensation_exists FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record';
    SELECT COUNT(*) INTO anomaly_exists FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly';

    IF (compensation_exists=1 AND message_exists=0)
       OR (anomaly_exists=1 AND (message_exists=0 OR compensation_exists=0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='reverse B6 table order';
    END IF;

    -- All validation precedes the first DDL because MySQL DDL auto-commits.
    IF message_exists=1 THEN
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_message_log';
        IF object_count <> 37 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log column count mismatch';
        END IF;
        SELECT GROUP_CONCAT(CONCAT(ordinal_position,':',column_name,':',LOWER(column_type),':',is_nullable)
                            ORDER BY ordinal_position SEPARATOR '|') INTO column_signature
        FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_message_log';
        IF column_signature <> '1:id:bigint:NO|2:message_id:varchar(128):NO|3:message_type:varchar(32):NO|4:publish_purpose:varchar(32):NO|5:business_key:varchar(128):NO|6:source_message_id:varchar(128):YES|7:source_message_id_hash:char(64):YES|8:source_message_id_prefix:varchar(64):YES|9:body_sha256:char(64):YES|10:body_size:bigint:YES|11:user_id:bigint:YES|12:coupon_id:bigint:YES|13:payload:text:NO|14:payload_schema_version:int:NO|15:exchange_name:varchar(128):NO|16:routing_key:varchar(128):NO|17:status:varchar(32):NO|18:dead_letter_status:varchar(16):NO|19:confirm_status:varchar(16):NO|20:returned:tinyint(1):NO|21:return_reply_code:int:YES|22:return_reply_text:varchar(255):YES|23:current_correlation_id:varchar(160):YES|24:publish_attempt:int:NO|25:consume_attempt:int:NO|26:processing_attempt:int:YES|27:fallback_attempt:int:NO|28:due_at:datetime(3):YES|29:next_retry_at:datetime(3):YES|30:locked_by:varchar(128):YES|31:locked_until:datetime(3):YES|32:version:bigint:NO|33:last_error:varchar(500):YES|34:created_at:datetime(3):NO|35:updated_at:datetime(3):NO|36:confirmed_at:datetime(3):YES|37:consumed_at:datetime(3):YES' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log column signature mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_message_log' AND NOT (
          (column_name IN ('id','message_id','message_type','publish_purpose','business_key',
             'source_message_id','source_message_id_hash','source_message_id_prefix','body_sha256','body_size',
             'user_id','coupon_id','payload','exchange_name','routing_key','status','return_reply_code',
             'return_reply_text','current_correlation_id','processing_attempt','due_at','next_retry_at',
             'locked_by','locked_until','last_error','confirmed_at','consumed_at') AND column_default IS NULL)
          OR (column_name='payload_schema_version' AND column_default='1')
          OR (column_name='dead_letter_status' AND column_default='NONE')
          OR (column_name='confirm_status' AND column_default='PENDING')
          OR (column_name IN ('returned','publish_attempt','consume_attempt','fallback_attempt','version') AND column_default='0')
          OR (column_name IN ('created_at','updated_at') AND UPPER(column_default)='CURRENT_TIMESTAMP(3)')
        );
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log default mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_message_log' AND NOT (
          (column_name='id' AND LOWER(extra)='auto_increment')
          OR (column_name='created_at' AND LOWER(extra)='default_generated')
          OR (column_name='updated_at'
              AND LOWER(extra)='default_generated on update current_timestamp(3)')
          OR (column_name NOT IN ('id','created_at','updated_at') AND extra='')
        );
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log extra mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.columns c JOIN information_schema.tables t
          ON t.table_schema=c.table_schema AND t.table_name=c.table_name
        WHERE c.table_schema=DATABASE() AND c.table_name='seckill_message_log'
          AND ((c.character_set_name IS NOT NULL AND c.collation_name<>t.table_collation)
               OR (c.character_set_name IS NULL AND c.collation_name IS NOT NULL));
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log column collation mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='seckill_message_log'
          AND engine='InnoDB' AND table_collation='utf8mb4_0900_ai_ci';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log engine/collation mismatch';
        END IF;
        SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name,':',
                            COALESCE(CAST(sub_part AS CHAR),'FULL'),':',index_type,':',
                            COALESCE(collation,'NONE'),':',is_visible)
                            ORDER BY index_name,seq_in_index SEPARATOR '|') INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='seckill_message_log';
        IF index_columns IS NULL OR index_columns <> 'idx_seckill_message_reconcile:1:1:coupon_id:FULL:BTREE:A:YES|idx_seckill_message_reconcile:1:2:user_id:FULL:BTREE:A:YES|idx_seckill_message_reconcile:1:3:status:FULL:BTREE:A:YES|idx_seckill_message_reconcile:1:4:id:FULL:BTREE:A:YES|idx_seckill_message_recovery:1:1:status:FULL:BTREE:A:YES|idx_seckill_message_recovery:1:2:next_retry_at:FULL:BTREE:A:YES|idx_seckill_message_recovery:1:3:id:FULL:BTREE:A:YES|PRIMARY:0:1:id:FULL:BTREE:A:YES|uk_seckill_message_business:0:1:message_type:FULL:BTREE:A:YES|uk_seckill_message_business:0:2:business_key:FULL:BTREE:A:YES|uk_seckill_message_id:0:1:message_id:FULL:BTREE:A:YES' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log index mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints
        WHERE constraint_schema=DATABASE() AND table_name='seckill_message_log'
          AND constraint_type='CHECK';
        IF object_count <> 2 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log check count mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc
        JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='seckill_message_log'
          AND tc.constraint_type='CHECK' AND tc.constraint_name='chk_seckill_message_attempts'
          AND tc.enforced='YES'
          AND SHA2(cc.check_clause,256)='3ed5d942aa3f10d839bb6b4cc33234736cdae1201b3f4c8ea757320a873f5668';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log check mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc
        JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='seckill_message_log'
          AND tc.constraint_type='CHECK' AND tc.constraint_name='chk_seckill_message_domains'
          AND tc.enforced='YES'
          AND SHA2(cc.check_clause,256)='0aa60f45fe34c963c5234727746271486f4d1c06d50dddc933c9c0bd45085495'
          AND LOWER(cc.check_clause) LIKE '%message_type%order_create%order_timeout%business_dead_letter%invalid_message%'
          AND LOWER(cc.check_clause) LIKE '%publish_purpose%initial%consume_retry%timeout_recovery%timeout_fallback%dead_letter%'
          AND LOWER(cc.check_clause) LIKE '%status%prepared%sent%broker_acked%processing%consumed%compensation_pending%compensated%manual_required%'
          AND LOWER(cc.check_clause) LIKE '%dead_letter_status%none%pending%acked%manual_required%'
          AND LOWER(cc.check_clause) LIKE '%confirm_status%pending%ack%nack%timeout%';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log domain check mismatch';
        END IF;
    END IF;

    IF compensation_exists=1 THEN
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record';
        IF object_count <> 20 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record column count mismatch';
        END IF;
        SELECT GROUP_CONCAT(CONCAT(ordinal_position,':',column_name,':',LOWER(column_type),':',is_nullable)
                            ORDER BY ordinal_position SEPARATOR '|') INTO column_signature
        FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record';
        IF column_signature <> '1:id:bigint:NO|2:compensation_action:varchar(32):NO|3:order_number:varchar(50):NO|4:user_id:bigint:NO|5:coupon_id:bigint:NO|6:first_reason:varchar(32):NO|7:last_reason:varchar(32):NO|8:evidence_mask:bigint:NO|9:status:varchar(32):NO|10:attempt_count:int:NO|11:next_retry_at:datetime(3):YES|12:locked_by:varchar(128):YES|13:locked_until:datetime(3):YES|14:last_result:varchar(64):YES|15:last_error:varchar(500):YES|16:version:bigint:NO|17:redis_applied_at:datetime(3):YES|18:created_at:datetime(3):NO|19:updated_at:datetime(3):NO|20:completed_at:datetime(3):YES' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record column signature mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record' AND NOT (
          (column_name IN ('id','compensation_action','order_number','user_id','coupon_id','first_reason',
             'last_reason','next_retry_at','locked_by','locked_until','last_result','last_error',
             'redis_applied_at','completed_at') AND column_default IS NULL)
          OR (column_name IN ('evidence_mask','attempt_count','version') AND column_default='0')
          OR (column_name='status' AND column_default='PENDING')
          OR (column_name IN ('created_at','updated_at') AND UPPER(column_default)='CURRENT_TIMESTAMP(3)')
        );
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record default mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record' AND NOT (
          (column_name='id' AND LOWER(extra)='auto_increment')
          OR (column_name='created_at' AND LOWER(extra)='default_generated')
          OR (column_name='updated_at'
              AND LOWER(extra)='default_generated on update current_timestamp(3)')
          OR (column_name NOT IN ('id','created_at','updated_at') AND extra='')
        );
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record extra mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.columns c JOIN information_schema.tables t
          ON t.table_schema=c.table_schema AND t.table_name=c.table_name
        WHERE c.table_schema=DATABASE() AND c.table_name='seckill_compensation_record'
          AND ((c.character_set_name IS NOT NULL AND c.collation_name<>t.table_collation)
               OR (c.character_set_name IS NULL AND c.collation_name IS NOT NULL));
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record column collation mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record'
          AND engine='InnoDB' AND table_collation='utf8mb4_0900_ai_ci';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record engine/collation mismatch';
        END IF;
        SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name,':',
                            COALESCE(CAST(sub_part AS CHAR),'FULL'),':',index_type,':',
                            COALESCE(collation,'NONE'),':',is_visible)
                            ORDER BY index_name,seq_in_index SEPARATOR '|') INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record';
        IF index_columns IS NULL OR index_columns <> 'idx_seckill_compensation_coupon:1:1:coupon_id:FULL:BTREE:A:YES|idx_seckill_compensation_coupon:1:2:id:FULL:BTREE:A:YES|idx_seckill_compensation_recovery:1:1:status:FULL:BTREE:A:YES|idx_seckill_compensation_recovery:1:2:next_retry_at:FULL:BTREE:A:YES|idx_seckill_compensation_recovery:1:3:id:FULL:BTREE:A:YES|PRIMARY:0:1:id:FULL:BTREE:A:YES|uk_seckill_compensation_action_order:0:1:compensation_action:FULL:BTREE:A:YES|uk_seckill_compensation_action_order:0:2:order_number:FULL:BTREE:A:YES' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record index mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints
        WHERE constraint_schema=DATABASE() AND table_name='seckill_compensation_record'
          AND constraint_type='CHECK';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record check count mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc
        JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='seckill_compensation_record'
          AND tc.constraint_type='CHECK' AND tc.constraint_name='chk_seckill_compensation_attempt'
          AND tc.enforced='YES'
          AND SHA2(cc.check_clause,256)='54285477da8e7ccf60b6cd68d071e1e651fd043b95011ceab88cd7bd14388e62'
          AND REPLACE(REPLACE(REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),' ',''),'(',''),')','')
              ='attempt_count>=0';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record check mismatch';
        END IF;
    END IF;

    IF anomaly_exists=1 THEN
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly';
        IF object_count <> 13 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly column count mismatch';
        END IF;
        SELECT GROUP_CONCAT(CONCAT(ordinal_position,':',column_name,':',LOWER(column_type),':',is_nullable)
                            ORDER BY ordinal_position SEPARATOR '|') INTO column_signature
        FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly';
        IF column_signature <> '1:id:bigint:NO|2:anomaly_type:varchar(32):NO|3:coupon_id:bigint:NO|4:status:varchar(16):NO|5:occurrence_count:int:NO|6:clean_scan_count:int:NO|7:sample_user_id:bigint:YES|8:sample_order_number:varchar(50):YES|9:details_hash:char(64):NO|10:version:bigint:NO|11:first_seen_at:datetime(3):NO|12:last_seen_at:datetime(3):NO|13:resolved_at:datetime(3):YES' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly column signature mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly' AND NOT (
          (column_name IN ('id','anomaly_type','coupon_id','sample_user_id','sample_order_number',
             'details_hash','resolved_at') AND column_default IS NULL)
          OR (column_name='status' AND column_default='OPEN')
          OR (column_name='occurrence_count' AND column_default='1')
          OR (column_name IN ('clean_scan_count','version') AND column_default='0')
          OR (column_name IN ('first_seen_at','last_seen_at') AND UPPER(column_default)='CURRENT_TIMESTAMP(3)')
        );
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly default mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly' AND NOT (
          (column_name='id' AND LOWER(extra)='auto_increment')
          OR (column_name IN ('first_seen_at','last_seen_at') AND LOWER(extra)='default_generated')
          OR (column_name NOT IN ('id','first_seen_at','last_seen_at') AND extra='')
        );
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly extra mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.columns c JOIN information_schema.tables t
          ON t.table_schema=c.table_schema AND t.table_name=c.table_name
        WHERE c.table_schema=DATABASE() AND c.table_name='seckill_reconciliation_anomaly'
          AND ((c.character_set_name IS NOT NULL AND c.collation_name<>t.table_collation)
               OR (c.character_set_name IS NULL AND c.collation_name IS NOT NULL));
        IF object_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly column collation mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly'
          AND engine='InnoDB' AND table_collation='utf8mb4_0900_ai_ci';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly engine/collation mismatch';
        END IF;
        SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name,':',
                            COALESCE(CAST(sub_part AS CHAR),'FULL'),':',index_type,':',
                            COALESCE(collation,'NONE'),':',is_visible)
                            ORDER BY index_name,seq_in_index SEPARATOR '|') INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly';
        IF index_columns IS NULL OR index_columns <> 'idx_seckill_anomaly_status:1:1:status:FULL:BTREE:A:YES|idx_seckill_anomaly_status:1:2:last_seen_at:FULL:BTREE:A:YES|idx_seckill_anomaly_status:1:3:id:FULL:BTREE:A:YES|PRIMARY:0:1:id:FULL:BTREE:A:YES|uk_seckill_anomaly_type_coupon:0:1:anomaly_type:FULL:BTREE:A:YES|uk_seckill_anomaly_type_coupon:0:2:coupon_id:FULL:BTREE:A:YES' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly index mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints
        WHERE constraint_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly'
          AND constraint_type='CHECK';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly check count mismatch';
        END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc
        JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='seckill_reconciliation_anomaly'
          AND tc.constraint_type='CHECK' AND tc.constraint_name='chk_seckill_anomaly_counts'
          AND tc.enforced='YES'
          AND SHA2(cc.check_clause,256)='80dae036327755f58e911c0979e5b9f94c6340c3a030b10be84d2b1b1d25c94a'
          AND LOWER(cc.check_clause) LIKE '%occurrence_count%> 0%'
          AND LOWER(cc.check_clause) LIKE '%clean_scan_count%>= 0%'
          AND LOWER(cc.check_clause) LIKE '%coupon_id%>= 0%'
          AND LOWER(cc.check_clause) LIKE '%invalid_registry_member%';
        IF object_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly check mismatch';
        END IF;
    END IF;

    -- Exact schema is not enough for a legal rerun: existing state-machine facts must also be valid.
    IF message_exists=1 THEN
        SELECT COUNT(*) INTO row_count_value FROM seckill_message_log
        WHERE TRIM(message_id)='' OR TRIM(business_key)='' OR payload_schema_version<=0
           OR publish_attempt<0 OR consume_attempt<0 OR fallback_attempt<0
           OR publish_attempt>5 OR consume_attempt>3 OR fallback_attempt>3
          OR BINARY message_type NOT IN ('ORDER_CREATE','ORDER_TIMEOUT','BUSINESS_DEAD_LETTER','INVALID_MESSAGE')
          OR NOT ((BINARY message_type='ORDER_CREATE' AND BINARY publish_purpose IN ('INITIAL','CONSUME_RETRY'))
               OR (BINARY message_type='ORDER_TIMEOUT' AND BINARY publish_purpose IN ('TIMEOUT_RECOVERY','TIMEOUT_FALLBACK'))
               OR (BINARY message_type IN ('BUSINESS_DEAD_LETTER','INVALID_MESSAGE')
                   AND BINARY publish_purpose='DEAD_LETTER'))
          OR BINARY status NOT IN ('PREPARED','SENT','BROKER_ACKED','RETRY_PUBLISH_PENDING',
             'TIMEOUT_PUBLISH_PENDING','TIMEOUT_FALLBACK_PENDING','DEAD_LETTER_PUBLISH_PENDING','COMPENSATION_PENDING',
             'PROCESSING','CONSUMED','CONSUME_EXHAUSTED','COMPENSATED','MANUAL_REQUIRED')
          OR BINARY dead_letter_status NOT IN ('NONE','PENDING','ACKED','MANUAL_REQUIRED')
          OR BINARY confirm_status NOT IN ('PENDING','ACK','NACK','TIMEOUT')
          OR NOT ((BINARY message_type='ORDER_CREATE' AND
                    ((BINARY publish_purpose='INITIAL' AND BINARY status IN
                      ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED','COMPENSATION_PENDING','COMPENSATED','MANUAL_REQUIRED'))
                     OR (BINARY publish_purpose='CONSUME_RETRY' AND BINARY status IN
                      ('SENT','BROKER_ACKED','PROCESSING','CONSUMED','RETRY_PUBLISH_PENDING','CONSUME_EXHAUSTED','MANUAL_REQUIRED'))))
               OR (BINARY message_type='ORDER_TIMEOUT' AND BINARY publish_purpose IN ('TIMEOUT_RECOVERY','TIMEOUT_FALLBACK')
                   AND BINARY status IN ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED','TIMEOUT_PUBLISH_PENDING','TIMEOUT_FALLBACK_PENDING','CONSUME_EXHAUSTED','MANUAL_REQUIRED'))
               OR (BINARY message_type='BUSINESS_DEAD_LETTER' AND BINARY publish_purpose='DEAD_LETTER'
                   AND BINARY status IN ('PREPARED','SENT','BROKER_ACKED','DEAD_LETTER_PUBLISH_PENDING','MANUAL_REQUIRED'))
               OR (BINARY message_type='INVALID_MESSAGE' AND BINARY publish_purpose='DEAD_LETTER'
                   AND BINARY status='CONSUME_EXHAUSTED'))
          OR (message_type IN ('ORDER_CREATE','ORDER_TIMEOUT') AND
              (business_key NOT REGEXP '^[0-9]{1,50}$' OR user_id IS NULL OR user_id<=0
               OR coupon_id IS NULL OR coupon_id<=0))
          OR (message_type='ORDER_TIMEOUT' AND due_at IS NULL)
          OR (message_type='BUSINESS_DEAD_LETTER' AND
              (source_message_id IS NULL OR source_message_id_hash COLLATE utf8mb4_bin NOT REGEXP '^[0-9a-f]{64}$'))
          OR (status='PROCESSING' AND consume_attempt=0)
           OR (processing_attempt IS NOT NULL AND (processing_attempt<1 OR processing_attempt>3))
           OR (BINARY status='PROCESSING' AND
               (processing_attempt IS NULL OR processing_attempt<>consume_attempt))
           OR (BINARY status<>'PROCESSING' AND processing_attempt IS NOT NULL)
           OR (BINARY status='PROCESSING' AND (locked_by IS NULL OR locked_until IS NULL))
           OR (BINARY status<>'PROCESSING' AND (locked_by IS NOT NULL OR locked_until IS NOT NULL))
           OR (body_size IS NOT NULL AND body_size<0)
           OR returned NOT IN (0,1)
           OR (publish_attempt=0 AND current_correlation_id IS NOT NULL)
           OR (publish_attempt>0 AND (current_correlation_id IS NULL OR
               BINARY current_correlation_id<>BINARY CONCAT(message_id,':P',publish_attempt)))
           OR (BINARY message_type='ORDER_CREATE' AND BINARY message_id<>
               BINARY CONCAT('SECKILL_ORDER_CREATE:',business_key))
           OR (BINARY message_type='ORDER_TIMEOUT' AND BINARY message_id<>
               BINARY CONCAT('SECKILL_ORDER_TIMEOUT:',business_key))
           OR (BINARY message_type='INVALID_MESSAGE' AND
               (BINARY message_id<>BINARY CONCAT('INVALID:',business_key)
                OR body_sha256 IS NULL OR BINARY body_sha256<>BINARY business_key
                OR business_key COLLATE utf8mb4_bin NOT REGEXP '^[0-9a-f]{64}$'))
           OR (BINARY message_type='BUSINESS_DEAD_LETTER' AND
               (source_message_id IS NULL OR source_message_id_hash IS NULL
                OR CHAR_LENGTH(source_message_id)>115
                OR BINARY message_id<>BINARY CONCAT('SECKILL_DEAD:',source_message_id)
                OR BINARY business_key<>BINARY source_message_id
                OR BINARY source_message_id_hash<>BINARY SHA2(source_message_id,256)
                OR NOT EXISTS (SELECT 1 FROM seckill_message_log source
                    WHERE BINARY source.message_id=BINARY seckill_message_log.source_message_id
                      AND BINARY source.message_type IN ('ORDER_CREATE','ORDER_TIMEOUT','INVALID_MESSAGE'))))
           OR (BINARY message_type='ORDER_CREATE'
               AND BINARY status IN ('CONSUMED','CONSUME_EXHAUSTED') AND consume_attempt=0)
           OR (BINARY message_type='ORDER_TIMEOUT' AND BINARY status='CONSUME_EXHAUSTED'
               AND consume_attempt=0)
           OR (BINARY message_type='ORDER_TIMEOUT' AND BINARY status='CONSUMED'
               AND BINARY publish_purpose<>'TIMEOUT_FALLBACK' AND consume_attempt=0);
        IF row_count_value <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log contains invalid state';
        END IF;
    END IF;

    IF compensation_exists=1 THEN
        SELECT COUNT(*) INTO row_count_value FROM seckill_compensation_record
        WHERE BINARY compensation_action<>'RELEASE_RESERVATION'
          OR order_number NOT REGEXP '^[0-9]{1,50}$' OR user_id<=0 OR coupon_id<=0
          OR TRIM(first_reason)='' OR TRIM(last_reason)='' OR evidence_mask<=0
          OR attempt_count<0 OR attempt_count>10
          OR BINARY status NOT IN ('PENDING','IN_PROGRESS','RETRY_PENDING','SUCCEEDED','MANUAL_REQUIRED')
          OR (BINARY status='IN_PROGRESS' AND (locked_by IS NULL OR locked_until IS NULL))
          OR (BINARY status<>'IN_PROGRESS' AND (locked_by IS NOT NULL OR locked_until IS NOT NULL))
          OR (BINARY status IN ('SUCCEEDED','MANUAL_REQUIRED') AND next_retry_at IS NOT NULL)
          OR (BINARY status='SUCCEEDED' AND (redis_applied_at IS NULL OR completed_at IS NULL));
        IF row_count_value <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record contains invalid state';
        END IF;
    END IF;

    IF anomaly_exists=1 THEN
        SELECT COUNT(*) INTO row_count_value FROM seckill_reconciliation_anomaly
        WHERE TRIM(anomaly_type)='' OR coupon_id<0
          OR (coupon_id=0 AND BINARY anomaly_type<>'INVALID_REGISTRY_MEMBER')
          OR (coupon_id>0 AND BINARY anomaly_type='INVALID_REGISTRY_MEMBER')
          OR BINARY status NOT IN ('OPEN','RESOLVED') OR occurrence_count<=0 OR clean_scan_count<0
          OR details_hash COLLATE utf8mb4_bin NOT REGEXP '^[0-9a-f]{64}$'
          OR (status='OPEN' AND resolved_at IS NOT NULL)
          OR (status='RESOLVED' AND resolved_at IS NULL);
        IF row_count_value <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly contains invalid state';
        END IF;
    END IF;

    IF message_exists=0 AND (compensation_exists=1 OR anomaly_exists=1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='reverse B6 object order cannot continue';
    END IF;
    IF compensation_exists=0 AND anomaly_exists=1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='reverse B6 object order cannot continue';
    END IF;

    IF message_exists=1 AND compensation_exists=0 THEN
        SELECT COUNT(*) INTO row_count_value FROM seckill_message_log;
        IF row_count_value <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty B6 prefix cannot continue';
        END IF;
    END IF;
    IF compensation_exists=1 AND anomaly_exists=0 THEN
        SELECT (SELECT COUNT(*) FROM seckill_message_log)
             + (SELECT COUNT(*) FROM seckill_compensation_record) INTO row_count_value;
        IF row_count_value <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty B6 prefix cannot continue';
        END IF;
    END IF;

    IF order_number_nullable='YES' THEN
        SET @b6_order_not_null_sql=CONCAT(
          'ALTER TABLE seckill_order MODIFY COLUMN order_number VARCHAR(50) CHARACTER SET ',
          order_number_charset,' COLLATE ',order_number_collation,' NOT NULL COMMENT ',
          order_number_comment_literal);
        PREPARE b6_order_not_null_stmt FROM @b6_order_not_null_sql;
        EXECUTE b6_order_not_null_stmt;
        DEALLOCATE PREPARE b6_order_not_null_stmt;
    END IF;

    IF message_exists=0 THEN
        CREATE TABLE seckill_message_log (
          id BIGINT NOT NULL AUTO_INCREMENT,
          message_id VARCHAR(128) NOT NULL,
          message_type VARCHAR(32) NOT NULL,
          publish_purpose VARCHAR(32) NOT NULL,
          business_key VARCHAR(128) NOT NULL,
          source_message_id VARCHAR(128) DEFAULT NULL,
          source_message_id_hash CHAR(64) DEFAULT NULL,
          source_message_id_prefix VARCHAR(64) DEFAULT NULL,
          body_sha256 CHAR(64) DEFAULT NULL,
          body_size BIGINT DEFAULT NULL,
          user_id BIGINT DEFAULT NULL,
          coupon_id BIGINT DEFAULT NULL,
          payload TEXT NOT NULL,
          payload_schema_version INT NOT NULL DEFAULT 1,
          exchange_name VARCHAR(128) NOT NULL,
          routing_key VARCHAR(128) NOT NULL,
          status VARCHAR(32) NOT NULL,
          dead_letter_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
          confirm_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
          returned TINYINT(1) NOT NULL DEFAULT 0,
          return_reply_code INT DEFAULT NULL,
          return_reply_text VARCHAR(255) DEFAULT NULL,
          current_correlation_id VARCHAR(160) DEFAULT NULL,
          publish_attempt INT NOT NULL DEFAULT 0,
          consume_attempt INT NOT NULL DEFAULT 0,
          processing_attempt INT DEFAULT NULL,
          fallback_attempt INT NOT NULL DEFAULT 0,
          due_at DATETIME(3) DEFAULT NULL,
          next_retry_at DATETIME(3) DEFAULT NULL,
          locked_by VARCHAR(128) DEFAULT NULL,
          locked_until DATETIME(3) DEFAULT NULL,
          version BIGINT NOT NULL DEFAULT 0,
          last_error VARCHAR(500) DEFAULT NULL,
          created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
          confirmed_at DATETIME(3) DEFAULT NULL,
          consumed_at DATETIME(3) DEFAULT NULL,
          PRIMARY KEY (id),
          UNIQUE KEY uk_seckill_message_id (message_id),
          UNIQUE KEY uk_seckill_message_business (message_type,business_key),
          KEY idx_seckill_message_recovery (status,next_retry_at,id),
          KEY idx_seckill_message_reconcile (coupon_id,user_id,status,id),
          CONSTRAINT chk_seckill_message_attempts CHECK
            (publish_attempt>=0 AND publish_attempt<=5 AND consume_attempt>=0 AND consume_attempt<=3
             AND fallback_attempt>=0 AND fallback_attempt<=3
             AND (processing_attempt IS NULL OR (processing_attempt>=1 AND processing_attempt<=3))
             AND ((BINARY status='PROCESSING' AND processing_attempt=consume_attempt)
               OR (BINARY status<>'PROCESSING' AND processing_attempt IS NULL))
             AND ((BINARY status='PROCESSING' AND locked_by IS NOT NULL AND locked_until IS NOT NULL)
               OR (BINARY status<>'PROCESSING' AND locked_by IS NULL AND locked_until IS NULL))
             AND payload_schema_version>0),
          CONSTRAINT chk_seckill_message_domains CHECK
            (BINARY message_type IN ('ORDER_CREATE','ORDER_TIMEOUT','BUSINESS_DEAD_LETTER','INVALID_MESSAGE')
             AND BINARY publish_purpose IN ('INITIAL','CONSUME_RETRY','TIMEOUT_RECOVERY','TIMEOUT_FALLBACK','DEAD_LETTER')
             AND BINARY status IN ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED','RETRY_PUBLISH_PENDING',
               'TIMEOUT_PUBLISH_PENDING','TIMEOUT_FALLBACK_PENDING','DEAD_LETTER_PUBLISH_PENDING','CONSUME_EXHAUSTED',
               'COMPENSATION_PENDING','COMPENSATED','MANUAL_REQUIRED')
             AND BINARY dead_letter_status IN ('NONE','PENDING','ACKED','MANUAL_REQUIRED')
             AND BINARY confirm_status IN ('PENDING','ACK','NACK','TIMEOUT')
             AND ((BINARY message_type='ORDER_CREATE' AND
                    ((BINARY publish_purpose='INITIAL' AND BINARY status IN
                      ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED',
                       'COMPENSATION_PENDING','COMPENSATED','MANUAL_REQUIRED'))
                     OR (BINARY publish_purpose='CONSUME_RETRY' AND BINARY status IN
                      ('SENT','BROKER_ACKED','PROCESSING','CONSUMED','RETRY_PUBLISH_PENDING',
                       'CONSUME_EXHAUSTED','MANUAL_REQUIRED'))))
               OR (BINARY message_type='ORDER_TIMEOUT'
                   AND BINARY publish_purpose IN ('TIMEOUT_RECOVERY','TIMEOUT_FALLBACK')
                   AND BINARY status IN ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED',
                     'TIMEOUT_PUBLISH_PENDING','TIMEOUT_FALLBACK_PENDING','CONSUME_EXHAUSTED','MANUAL_REQUIRED'))
               OR (BINARY message_type='BUSINESS_DEAD_LETTER'
                   AND BINARY publish_purpose='DEAD_LETTER'
                   AND BINARY status IN ('PREPARED','SENT','BROKER_ACKED',
                     'DEAD_LETTER_PUBLISH_PENDING','MANUAL_REQUIRED'))
               OR (BINARY message_type='INVALID_MESSAGE'
                   AND BINARY publish_purpose='DEAD_LETTER'
                   AND BINARY status='CONSUME_EXHAUSTED')))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
        SET message_exists=1;
    ELSE
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='seckill_message_log'
          AND index_name IN ('PRIMARY','uk_seckill_message_id','uk_seckill_message_business');
        IF object_count <> 4 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_message_log definition mismatch';
        END IF;
    END IF;

    IF compensation_exists=0 THEN
        SELECT COUNT(*) INTO row_count_value FROM seckill_message_log;
        IF row_count_value <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty B6 prefix cannot continue';
        END IF;
        CREATE TABLE seckill_compensation_record (
          id BIGINT NOT NULL AUTO_INCREMENT,
          compensation_action VARCHAR(32) NOT NULL,
          order_number VARCHAR(50) NOT NULL,
          user_id BIGINT NOT NULL,
          coupon_id BIGINT NOT NULL,
          first_reason VARCHAR(32) NOT NULL,
          last_reason VARCHAR(32) NOT NULL,
          evidence_mask BIGINT NOT NULL DEFAULT 0,
          status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
          attempt_count INT NOT NULL DEFAULT 0,
          next_retry_at DATETIME(3) DEFAULT NULL,
          locked_by VARCHAR(128) DEFAULT NULL,
          locked_until DATETIME(3) DEFAULT NULL,
          last_result VARCHAR(64) DEFAULT NULL,
          last_error VARCHAR(500) DEFAULT NULL,
          version BIGINT NOT NULL DEFAULT 0,
          redis_applied_at DATETIME(3) DEFAULT NULL,
          created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
          completed_at DATETIME(3) DEFAULT NULL,
          PRIMARY KEY (id),
          UNIQUE KEY uk_seckill_compensation_action_order (compensation_action,order_number),
          KEY idx_seckill_compensation_recovery (status,next_retry_at,id),
          KEY idx_seckill_compensation_coupon (coupon_id,id),
          CONSTRAINT chk_seckill_compensation_attempt CHECK (attempt_count>=0)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
        SET compensation_exists=1;
    ELSE
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='seckill_compensation_record'
          AND index_name='uk_seckill_compensation_action_order' AND non_unique=0;
        IF object_count <> 2 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_compensation_record definition mismatch';
        END IF;
    END IF;

    IF anomaly_exists=0 THEN
        SELECT (SELECT COUNT(*) FROM seckill_message_log)
             + (SELECT COUNT(*) FROM seckill_compensation_record) INTO row_count_value;
        IF row_count_value <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty B6 prefix cannot continue';
        END IF;
        CREATE TABLE seckill_reconciliation_anomaly (
          id BIGINT NOT NULL AUTO_INCREMENT,
          anomaly_type VARCHAR(32) NOT NULL,
          coupon_id BIGINT NOT NULL,
          status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
          occurrence_count INT NOT NULL DEFAULT 1,
          clean_scan_count INT NOT NULL DEFAULT 0,
          sample_user_id BIGINT DEFAULT NULL,
          sample_order_number VARCHAR(50) DEFAULT NULL,
          details_hash CHAR(64) NOT NULL,
          version BIGINT NOT NULL DEFAULT 0,
          first_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
          last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
          resolved_at DATETIME(3) DEFAULT NULL,
          PRIMARY KEY (id),
          UNIQUE KEY uk_seckill_anomaly_type_coupon (anomaly_type,coupon_id),
          KEY idx_seckill_anomaly_status (status,last_seen_at,id),
          CONSTRAINT chk_seckill_anomaly_counts CHECK
            (occurrence_count>0 AND clean_scan_count>=0 AND coupon_id>=0
             AND ((coupon_id=0 AND BINARY anomaly_type='INVALID_REGISTRY_MEMBER')
               OR (coupon_id>0 AND BINARY anomaly_type<>'INVALID_REGISTRY_MEMBER')))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
    ELSE
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='seckill_reconciliation_anomaly'
          AND index_name='uk_seckill_anomaly_type_coupon' AND non_unique=0;
        IF object_count <> 2 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='seckill_reconciliation_anomaly definition mismatch';
        END IF;
    END IF;
END$$
DELIMITER ;

CALL migrate_b6_seckill_mq_reliability();
DROP PROCEDURE migrate_b6_seckill_mq_reliability;
