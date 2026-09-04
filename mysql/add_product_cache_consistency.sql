-- B8 product cache versioning and recoverable ES projection migration.
-- Requires MySQL 8.0.16 or newer because B8 relies on enforced CHECK constraints.
-- Forward-only: only exact empty prefixes in state -> revision -> task -> reconcile order may continue.

DROP PROCEDURE IF EXISTS migrate_b8_product_cache_consistency;
DELIMITER $$
CREATE PROCEDURE migrate_b8_product_cache_consistency()
BEGIN
    DECLARE major_version INT DEFAULT 0;
    DECLARE minor_version INT DEFAULT 0;
    DECLARE object_count INT DEFAULT 0;
    DECLARE state_exists INT DEFAULT 0;
    DECLARE revision_exists INT DEFAULT 0;
    DECLARE task_exists INT DEFAULT 0;
    DECLARE run_exists INT DEFAULT 0;
    DECLARE dirty_count BIGINT DEFAULT 0;
    DECLARE seed_version BIGINT UNSIGNED DEFAULT 0;

    SELECT CAST(SUBSTRING_INDEX(VERSION(),'.',1) AS UNSIGNED),
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(),'.',2),'.',-1) AS UNSIGNED)
      INTO major_version,minor_version;
    IF major_version < 8 OR (major_version=8 AND minor_version<0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B8 migration requires MySQL 8.0.16 or newer';
    END IF;
    IF major_version=8 AND minor_version=0
       AND CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(),'.',3),'.',-1) AS UNSIGNED)<16 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B8 migration requires MySQL 8.0.16 or newer';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='product' AND engine='InnoDB';
    IF object_count<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B8 migration requires InnoDB product table';
    END IF;
    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='product'
      AND ((column_name='id' AND data_type='bigint')
        OR (column_name='status' AND data_type IN ('int','tinyint'))
        OR (column_name='sales' AND data_type='int' AND is_nullable='YES' AND column_default='0'));
    IF object_count<>3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B8 product id/status/sales definition mismatch';
    END IF;
    SELECT COUNT(*) INTO dirty_count FROM product
    WHERE id<=0 OR status IS NULL OR status NOT IN (0,1) OR sales<0;
    IF dirty_count<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B8 product contains invalid id, status or sales';
    END IF;

    SELECT COUNT(*) INTO state_exists FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='product_catalog_state';
    SELECT COUNT(*) INTO revision_exists FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='product_catalog_revision';
    SELECT COUNT(*) INTO task_exists FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='product_projection_task';
    SELECT COUNT(*) INTO run_exists FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run';

    IF (revision_exists=1 AND state_exists=0)
       OR (task_exists=1 AND (state_exists=0 OR revision_exists=0))
       OR (run_exists=1 AND (state_exists=0 OR revision_exists=0 OR task_exists=0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='reverse B8 table order';
    END IF;

    -- Validate every existing object before the first DDL. Unknown shapes are never dropped or repaired.
    IF state_exists=1 THEN
        SELECT COUNT(*) INTO object_count FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='product_catalog_state'
          AND engine='InnoDB' AND table_collation='utf8mb4_0900_ai_ci';
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_state table mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_catalog_state'
          AND ((column_name='id' AND column_type='tinyint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='list_version' AND column_type='bigint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='updated_at' AND column_type='datetime(3)' AND is_nullable='NO'
                AND column_default='CURRENT_TIMESTAMP(3)'
                AND LOWER(extra)='default_generated on update current_timestamp(3)'));
        IF object_count<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_state column mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_catalog_state';
        IF object_count<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_state unexpected column'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_catalog_state'
          AND index_name='PRIMARY' AND column_name='id' AND non_unique=0;
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_state primary key mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_catalog_state';
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_state index mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints
        WHERE constraint_schema=DATABASE() AND table_name='product_catalog_state'
          AND constraint_type='CHECK' AND enforced='YES';
        IF object_count<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_state CHECK mismatch'; END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='product_catalog_state' AND tc.enforced='YES'
          AND ((tc.constraint_name='chk_product_catalog_singleton'
                AND LOWER(REPLACE(REPLACE(REPLACE(cc.check_clause,' ',''),'`',''),'\\',''))='(id=1)')
            OR (tc.constraint_name='chk_product_catalog_version'
                AND LOWER(REPLACE(REPLACE(REPLACE(cc.check_clause,' ',''),'`',''),'\\',''))
                    ='(list_versionbetween1and9007199254740991)'));
        IF object_count<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_state CHECK definition mismatch'; END IF;
    END IF;
    IF revision_exists=1 THEN
        SELECT COUNT(*) INTO object_count FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='product_catalog_revision'
          AND engine='InnoDB' AND table_collation='utf8mb4_0900_ai_ci';
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision table mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_catalog_revision'
          AND ((column_name='product_id' AND column_type='bigint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='item_version' AND column_type='bigint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='item_state' AND column_type='varchar(16)' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='es_locked_by' AND column_type='varchar(64)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='es_locked_until' AND column_type='datetime(3)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='updated_at' AND column_type='datetime(3)' AND is_nullable='NO'
                AND column_default='CURRENT_TIMESTAMP(3)'
                AND LOWER(extra)='default_generated on update current_timestamp(3)'));
        IF object_count<>6 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision column mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_catalog_revision';
        IF object_count<>6 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision unexpected column'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_catalog_revision'
          AND index_name='PRIMARY' AND column_name='product_id' AND non_unique=0;
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision primary key mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_catalog_revision'
          AND ((index_name='PRIMARY' AND seq_in_index=1 AND column_name='product_id' AND non_unique=0)
            OR (index_name='idx_product_revision_es_lease' AND seq_in_index=1
                AND column_name='es_locked_until' AND non_unique=1)
            OR (index_name='idx_product_revision_es_lease' AND seq_in_index=2
                AND column_name='product_id' AND non_unique=1));
        IF object_count<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision index definition mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_catalog_revision';
        IF object_count<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision unexpected index'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints
        WHERE constraint_schema=DATABASE() AND table_name='product_catalog_revision'
          AND constraint_type='CHECK' AND enforced='YES';
        IF object_count<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision CHECK mismatch'; END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='product_catalog_revision' AND tc.enforced='YES'
          AND ((tc.constraint_name='chk_product_revision_version'
                AND LOWER(REPLACE(REPLACE(REPLACE(cc.check_clause,' ',''),'`',''),'\\',''))
                    ='(item_versionbetween1and9007199254740991)')
            OR (tc.constraint_name='chk_product_revision_state'
                AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    cc.check_clause,' ',''),'`',''),'_utf8mb4',''),'cast(',''),
                    'ascharcharsetbinary)',''),'\\',''))
                    ='(item_statein(''active'',''inactive'',''deleted''))'));
        IF object_count<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_catalog_revision CHECK definition mismatch'; END IF;
    END IF;
    IF task_exists=1 THEN
        SELECT COUNT(*) INTO object_count FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='product_projection_task'
          AND engine='InnoDB' AND table_collation='utf8mb4_0900_ai_ci';
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_task table mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_projection_task'
          AND ((column_name='id' AND column_type='bigint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='auto_increment')
            OR (column_name='target' AND column_type='varchar(10)' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='product_id' AND column_type='bigint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='catalog_version' AND column_type='bigint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='operation' AND column_type='varchar(10)' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='payload' AND data_type='longtext' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='payload_sha256' AND column_type='char(64)' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='status' AND column_type='varchar(24)' AND is_nullable='NO'
                AND column_default='PENDING' AND extra='')
            OR (column_name IN ('attempt_count','claim_count','repair_count','manual_replay_count')
                AND column_type='int unsigned' AND is_nullable='NO' AND column_default='0' AND extra='')
            OR (column_name='next_retry_at' AND column_type='datetime(3)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='locked_by' AND column_type='varchar(64)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='locked_until' AND column_type='datetime(3)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='last_error_summary' AND column_type='varchar(500)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='created_at' AND column_type='datetime(3)' AND is_nullable='NO'
                AND column_default='CURRENT_TIMESTAMP(3)' AND LOWER(extra)='default_generated')
            OR (column_name='updated_at' AND column_type='datetime(3)' AND is_nullable='NO'
                AND column_default='CURRENT_TIMESTAMP(3)'
                AND LOWER(extra)='default_generated on update current_timestamp(3)')
            OR (column_name IN ('completed_at','last_replayed_at') AND column_type='datetime(3)'
                AND is_nullable='YES' AND column_default IS NULL AND extra=''));
        IF object_count<>20 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_task column mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_projection_task';
        IF object_count<>20 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_task unexpected column'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_projection_task'
          AND index_name='uk_product_projection_fact' AND non_unique=0;
        IF object_count<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_task unique key mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_projection_task'
          AND ((index_name='PRIMARY' AND seq_in_index=1 AND column_name='id' AND non_unique=0)
            OR (index_name='uk_product_projection_fact' AND seq_in_index=1 AND column_name='target' AND non_unique=0)
            OR (index_name='uk_product_projection_fact' AND seq_in_index=2 AND column_name='product_id' AND non_unique=0)
            OR (index_name='uk_product_projection_fact' AND seq_in_index=3 AND column_name='catalog_version' AND non_unique=0)
            OR (index_name='idx_product_projection_recovery' AND seq_in_index=1 AND column_name='target' AND non_unique=1)
            OR (index_name='idx_product_projection_recovery' AND seq_in_index=2 AND column_name='status' AND non_unique=1)
            OR (index_name='idx_product_projection_recovery' AND seq_in_index=3 AND column_name='next_retry_at' AND non_unique=1)
            OR (index_name='idx_product_projection_recovery' AND seq_in_index=4 AND column_name='id' AND non_unique=1)
            OR (index_name='idx_product_projection_product' AND seq_in_index=1 AND column_name='product_id' AND non_unique=1)
            OR (index_name='idx_product_projection_product' AND seq_in_index=2 AND column_name='catalog_version' AND non_unique=1)
            OR (index_name='idx_product_projection_product' AND seq_in_index=3 AND column_name='target' AND non_unique=1));
        IF object_count<>11 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_task index definition mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_projection_task';
        IF object_count<>11 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_task unexpected index'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints
        WHERE constraint_schema=DATABASE() AND table_name='product_projection_task'
          AND constraint_type='CHECK' AND enforced='YES';
        IF object_count<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_task CHECK mismatch'; END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc
        JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='product_projection_task'
          AND tc.constraint_name='chk_product_projection_version' AND tc.enforced='YES'
          AND LOWER(REPLACE(REPLACE(REPLACE(cc.check_clause,' ',''),'`',''),'\\',''))
              ='(catalog_versionbetween1and9007199254740991)';
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product projection version CHECK mismatch'; END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='product_projection_task' AND tc.enforced='YES'
          AND ((tc.constraint_name='chk_product_projection_attempts'
                AND LOWER(REPLACE(REPLACE(REPLACE(cc.check_clause,' ',''),'`',''),'\\',''))
                    ='((attempt_count<=8)and(claim_count>=attempt_count)and(repair_count<=8))')
            OR (tc.constraint_name='chk_product_projection_domain'
                AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    cc.check_clause,' ',''),'`',''),'_utf8mb4',''),'cast(',''),
                    'ascharcharsetbinary)',''),'\\',''))
                    ='((statusin(''pending'',''processing'',''retry_wait'',''succeeded'',''superseded'',''failed_terminal''))and(((target=''redis'')and(operation=''publish''))or((target=''es'')and(operationin(''upsert'',''delete'')))))'));
        IF object_count<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product projection CHECK definition mismatch'; END IF;
    END IF;
    IF run_exists=1 THEN
        SELECT COUNT(*) INTO object_count FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run'
          AND engine='InnoDB' AND table_collation='utf8mb4_0900_ai_ci';
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile table mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run'
          AND ((column_name='id' AND column_type='bigint unsigned' AND is_nullable='NO'
                AND column_default IS NULL AND extra='auto_increment')
            OR (column_name IN ('mode','phase') AND column_type='varchar(16)' AND is_nullable='NO'
                AND column_default IS NULL AND extra='')
            OR (column_name='status' AND column_type='varchar(24)' AND is_nullable='NO'
                AND column_default='PENDING' AND extra='')
            OR (column_name='cursor_payload' AND data_type='text' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name IN ('scan_count','drift_count','repair_count')
                AND column_type='bigint unsigned' AND is_nullable='NO' AND column_default='0' AND extra='')
            OR (column_name IN ('clean_verify_count','attempt_count')
                AND column_type='int unsigned' AND is_nullable='NO' AND column_default='0' AND extra='')
            OR (column_name='next_retry_at' AND column_type='datetime(3)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='locked_by' AND column_type='varchar(64)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='locked_until' AND column_type='datetime(3)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name='last_error_summary' AND column_type='varchar(500)' AND is_nullable='YES'
                AND column_default IS NULL AND extra='')
            OR (column_name IN ('started_at','completed_at') AND column_type='datetime(3)'
                AND is_nullable='YES' AND column_default IS NULL AND extra='')
            OR (column_name='updated_at' AND column_type='datetime(3)' AND is_nullable='NO'
                AND column_default='CURRENT_TIMESTAMP(3)'
                AND LOWER(extra)='default_generated on update current_timestamp(3)')
            OR (column_name='active_slot' AND column_type='tinyint' AND is_nullable='YES'
                AND column_default IS NULL AND LOWER(extra)='stored generated'));
        IF object_count<>18 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product_projection_reconcile_run column mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run';
        IF object_count<>18 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile unexpected column'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run'
          AND column_name='active_slot'
          AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression,' ',''),'`',''),
                    '_utf8mb4',''),'\\',''))
              ='(casewhen(statusin(''pending'',''running'',''retry_wait''))then1elsenullend)';
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile generated expression mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run'
          AND index_name='uk_product_reconcile_active' AND non_unique=0;
        IF object_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile active key mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run'
          AND ((index_name='PRIMARY' AND seq_in_index=1 AND column_name='id' AND non_unique=0)
            OR (index_name='uk_product_reconcile_active' AND seq_in_index=1 AND column_name='active_slot' AND non_unique=0)
            OR (index_name='idx_product_reconcile_recovery' AND seq_in_index=1 AND column_name='status' AND non_unique=1)
            OR (index_name='idx_product_reconcile_recovery' AND seq_in_index=2 AND column_name='next_retry_at' AND non_unique=1)
            OR (index_name='idx_product_reconcile_recovery' AND seq_in_index=3 AND column_name='id' AND non_unique=1));
        IF object_count<>5 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile index definition mismatch'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='product_projection_reconcile_run';
        IF object_count<>5 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile unexpected index'; END IF;
        SELECT COUNT(*) INTO object_count FROM information_schema.table_constraints
        WHERE constraint_schema=DATABASE() AND table_name='product_projection_reconcile_run'
          AND constraint_type='CHECK' AND enforced='YES';
        IF object_count<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile CHECK mismatch'; END IF;
        SELECT COUNT(*) INTO object_count
        FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc
          ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
        WHERE tc.constraint_schema=DATABASE() AND tc.table_name='product_projection_reconcile_run' AND tc.enforced='YES'
          AND ((tc.constraint_name='chk_product_reconcile_domain'
                AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    cc.check_clause,' ',''),'`',''),'_utf8mb4',''),'cast(',''),
                    'ascharcharsetbinary)',''),'\\',''))
                    ='((modein(''cutover'',''periodic''))and(phasein(''mysql_scan'',''es_scan'',''verify''))and(statusin(''pending'',''running'',''retry_wait'',''succeeded'',''failed_terminal'')))')
            OR (tc.constraint_name='chk_product_reconcile_counts'
                AND LOWER(REPLACE(REPLACE(REPLACE(cc.check_clause,' ',''),'`',''),'\\',''))
                    ='((clean_verify_count<=2)and(attempt_count<=8))'));
        IF object_count<>2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='product reconcile CHECK definition mismatch'; END IF;
    END IF;

    -- Only an exact empty forward prefix may be resumed after interrupted DDL.
    IF state_exists=1 AND revision_exists=0 THEN
        SELECT COUNT(*) INTO dirty_count FROM product_catalog_state;
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty B8 state prefix'; END IF;
    END IF;
    IF revision_exists=1 AND task_exists=0 THEN
        SELECT (SELECT COUNT(*) FROM product_catalog_state)
             + (SELECT COUNT(*) FROM product_catalog_revision) INTO dirty_count;
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty B8 revision prefix'; END IF;
    END IF;
    IF task_exists=1 AND run_exists=0 THEN
        SELECT (SELECT COUNT(*) FROM product_catalog_state)
             + (SELECT COUNT(*) FROM product_catalog_revision)
             + (SELECT COUNT(*) FROM product_projection_task) INTO dirty_count;
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty B8 task prefix'; END IF;
    END IF;

    -- Existing complete data must already obey the B8 state machine before any DDL/DML.
    IF state_exists=1 AND revision_exists=1 AND task_exists=1 AND run_exists=1 THEN
        SELECT COUNT(*) INTO dirty_count FROM product_catalog_state
        WHERE id<>1 OR list_version NOT BETWEEN 1 AND 9007199254740991;
        IF dirty_count<>0 OR (SELECT COUNT(*) FROM product_catalog_state)<>1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='invalid product catalog singleton';
        END IF;
        SELECT COUNT(*) INTO dirty_count
        FROM product_catalog_revision r LEFT JOIN product p ON p.id=r.product_id
        WHERE r.product_id<=0 OR r.item_version NOT BETWEEN 1 AND 9007199254740991
           OR r.item_version>(SELECT list_version FROM product_catalog_state WHERE id=1)
           OR r.item_state NOT IN ('ACTIVE','INACTIVE','DELETED')
           OR (r.item_state='ACTIVE' AND (p.id IS NULL OR p.status<>1))
           OR (r.item_state='INACTIVE' AND (p.id IS NULL OR p.status<>0))
           OR (r.item_state='DELETED' AND p.id IS NOT NULL);
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='invalid product catalog revision'; END IF;
        SELECT COUNT(*) INTO dirty_count FROM product_projection_task
        WHERE target NOT IN ('REDIS','ES')
           OR operation NOT IN ('PUBLISH','UPSERT','DELETE')
           OR status NOT IN ('PENDING','PROCESSING','RETRY_WAIT','SUCCEEDED','SUPERSEDED','FAILED_TERMINAL')
           OR attempt_count<0 OR attempt_count>8 OR claim_count<0 OR repair_count<0
           OR payload_sha256 NOT REGEXP '^[0-9a-f]{64}$'
           OR payload_sha256<>LOWER(SHA2(CONVERT(payload USING utf8mb4),256));
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='invalid product projection task'; END IF;
    END IF;

    IF state_exists=0 THEN
        CREATE TABLE product_catalog_state (
          id TINYINT UNSIGNED NOT NULL,
          list_version BIGINT UNSIGNED NOT NULL,
          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
          PRIMARY KEY (id),
          CONSTRAINT chk_product_catalog_singleton CHECK (id=1),
          CONSTRAINT chk_product_catalog_version CHECK (list_version BETWEEN 1 AND 9007199254740991)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
        SET state_exists=1;
    END IF;

    IF revision_exists=0 THEN
        SELECT COUNT(*) INTO dirty_count FROM product_catalog_state;
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty state before revision DDL'; END IF;
        CREATE TABLE product_catalog_revision (
          product_id BIGINT UNSIGNED NOT NULL,
          item_version BIGINT UNSIGNED NOT NULL,
          item_state VARCHAR(16) NOT NULL,
          es_locked_by VARCHAR(64) DEFAULT NULL,
          es_locked_until DATETIME(3) DEFAULT NULL,
          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
          PRIMARY KEY (product_id),
          KEY idx_product_revision_es_lease (es_locked_until,product_id),
          CONSTRAINT chk_product_revision_version CHECK (item_version BETWEEN 1 AND 9007199254740991),
          CONSTRAINT chk_product_revision_state CHECK (BINARY item_state IN ('ACTIVE','INACTIVE','DELETED'))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
        SET revision_exists=1;
    END IF;

    IF task_exists=0 THEN
        SELECT (SELECT COUNT(*) FROM product_catalog_state)
             + (SELECT COUNT(*) FROM product_catalog_revision) INTO dirty_count;
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty prefix before task DDL'; END IF;
        CREATE TABLE product_projection_task (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          target VARCHAR(10) NOT NULL,
          product_id BIGINT UNSIGNED NOT NULL,
          catalog_version BIGINT UNSIGNED NOT NULL,
          operation VARCHAR(10) NOT NULL,
          payload LONGTEXT NOT NULL,
          payload_sha256 CHAR(64) NOT NULL,
          status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
          attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
          claim_count INT UNSIGNED NOT NULL DEFAULT 0,
          repair_count INT UNSIGNED NOT NULL DEFAULT 0,
          next_retry_at DATETIME(3) DEFAULT NULL,
          locked_by VARCHAR(64) DEFAULT NULL,
          locked_until DATETIME(3) DEFAULT NULL,
          last_error_summary VARCHAR(500) DEFAULT NULL,
          created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
          completed_at DATETIME(3) DEFAULT NULL,
          manual_replay_count INT UNSIGNED NOT NULL DEFAULT 0,
          last_replayed_at DATETIME(3) DEFAULT NULL,
          PRIMARY KEY (id),
          UNIQUE KEY uk_product_projection_fact (target,product_id,catalog_version),
          KEY idx_product_projection_recovery (target,status,next_retry_at,id),
          KEY idx_product_projection_product (product_id,catalog_version,target),
          CONSTRAINT chk_product_projection_version CHECK (catalog_version BETWEEN 1 AND 9007199254740991),
          CONSTRAINT chk_product_projection_attempts CHECK
            (attempt_count<=8 AND claim_count>=attempt_count AND repair_count<=8),
          CONSTRAINT chk_product_projection_domain CHECK
            (BINARY status IN ('PENDING','PROCESSING','RETRY_WAIT','SUCCEEDED','SUPERSEDED','FAILED_TERMINAL')
             AND ((BINARY target='REDIS' AND BINARY operation='PUBLISH')
               OR (BINARY target='ES' AND BINARY operation IN ('UPSERT','DELETE'))))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
        SET task_exists=1;
    END IF;

    IF run_exists=0 THEN
        SELECT (SELECT COUNT(*) FROM product_catalog_state)
             + (SELECT COUNT(*) FROM product_catalog_revision)
             + (SELECT COUNT(*) FROM product_projection_task) INTO dirty_count;
        IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='nonempty prefix before reconcile DDL'; END IF;
        CREATE TABLE product_projection_reconcile_run (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          mode VARCHAR(16) NOT NULL,
          phase VARCHAR(16) NOT NULL,
          status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
          cursor_payload TEXT DEFAULT NULL,
          scan_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
          drift_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
          repair_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
          clean_verify_count INT UNSIGNED NOT NULL DEFAULT 0,
          attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
          next_retry_at DATETIME(3) DEFAULT NULL,
          locked_by VARCHAR(64) DEFAULT NULL,
          locked_until DATETIME(3) DEFAULT NULL,
          last_error_summary VARCHAR(500) DEFAULT NULL,
          started_at DATETIME(3) DEFAULT NULL,
          completed_at DATETIME(3) DEFAULT NULL,
          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
          active_slot TINYINT GENERATED ALWAYS AS
            (CASE WHEN status IN ('PENDING','RUNNING','RETRY_WAIT') THEN 1 ELSE NULL END) STORED,
          PRIMARY KEY (id),
          UNIQUE KEY uk_product_reconcile_active (active_slot),
          KEY idx_product_reconcile_recovery (status,next_retry_at,id),
          CONSTRAINT chk_product_reconcile_domain CHECK
            (BINARY mode IN ('CUTOVER','PERIODIC')
             AND BINARY phase IN ('MYSQL_SCAN','ES_SCAN','VERIFY')
             AND BINARY status IN ('PENDING','RUNNING','RETRY_WAIT','SUCCEEDED','FAILED_TERMINAL')),
          CONSTRAINT chk_product_reconcile_counts CHECK
            (clean_verify_count<=2 AND attempt_count<=8)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
        SET run_exists=1;
    END IF;

    -- The four tables now exist. Normalize legacy NULL sales while preserving nullable rollback compatibility.
    UPDATE product SET sales=0 WHERE sales IS NULL;
    SET seed_version=CAST(UNIX_TIMESTAMP(UTC_TIMESTAMP(3))*1000 AS UNSIGNED);
    IF seed_version<1 OR seed_version>9007199254740991 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B8 seed version outside safe domain';
    END IF;
    INSERT INTO product_catalog_state(id,list_version,updated_at)
    SELECT 1,seed_version,CURRENT_TIMESTAMP(3)
    WHERE NOT EXISTS (SELECT 1 FROM product_catalog_state WHERE id=1);

    INSERT INTO product_catalog_revision(product_id,item_version,item_state,updated_at)
    SELECT p.id,s.list_version,CASE WHEN p.status=1 THEN 'ACTIVE' ELSE 'INACTIVE' END,CURRENT_TIMESTAMP(3)
    FROM product p JOIN product_catalog_state s ON s.id=1
    LEFT JOIN product_catalog_revision r ON r.product_id=p.id
    WHERE r.product_id IS NULL;

    SELECT COUNT(*) INTO dirty_count FROM product p
    LEFT JOIN product_catalog_revision r ON r.product_id=p.id
    WHERE r.product_id IS NULL OR (p.status=1 AND r.item_state<>'ACTIVE')
       OR (p.status=0 AND r.item_state<>'INACTIVE');
    IF dirty_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='B8 revision backfill mismatch'; END IF;
END$$
DELIMITER ;

CALL migrate_b8_product_cache_consistency();
DROP PROCEDURE migrate_b8_product_cache_consistency;
