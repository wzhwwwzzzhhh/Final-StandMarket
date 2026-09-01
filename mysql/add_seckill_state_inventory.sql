-- B5 秒杀订单状态、活动唯一性与取消后重新参与迁移。
-- MySQL 8；只允许从精确旧定义迁移，合法 B5 定义可重复执行。

DROP PROCEDURE IF EXISTS migrate_b5_seckill_state_inventory;
DELIMITER $$
CREATE PROCEDURE migrate_b5_seckill_state_inventory()
BEGIN
    DECLARE object_count INT DEFAULT 0;
    DECLARE valid_count INT DEFAULT 0;
    DECLARE old_index_columns VARCHAR(255);
    DECLARE new_index_columns VARCHAR(255);
    DECLARE check_expression TEXT;
    DECLARE generation_expression TEXT;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'seckill_order';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B5 migration requires seckill_order table';
    END IF;

    SELECT COUNT(*) INTO valid_count
    FROM seckill_order
    WHERE status IS NULL OR status NOT IN (1, 2, 3);
    IF valid_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'seckill_order contains null or unknown status';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
      AND column_name = 'active_marker';

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
      AND index_name = 'uk_seckill_order_active_user_coupon';

    IF object_count = 0 AND valid_count = 0 THEN
        SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') INTO old_index_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
          AND index_name = 'idx_user_coupon';
        SELECT COUNT(*) INTO object_count
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
          AND index_name = 'idx_user_coupon' AND non_unique = 0;
        IF old_index_columns <> 'user_id,coupon_id' OR object_count <> 2 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'idx_user_coupon definition mismatch';
        END IF;

        ALTER TABLE seckill_order
            MODIFY COLUMN status INT NOT NULL DEFAULT 1 COMMENT '状态 1:待支付 2:已支付 3:已取消';

        ALTER TABLE seckill_order
            ADD CONSTRAINT chk_seckill_order_status_b5 CHECK (status IN (1, 2, 3));

        ALTER TABLE seckill_order
            ADD COLUMN active_marker TINYINT
                GENERATED ALWAYS AS (CASE WHEN status = 3 THEN NULL ELSE 1 END) STORED
                AFTER pay_time;

        ALTER TABLE seckill_order
            ADD UNIQUE INDEX uk_seckill_order_active_user_coupon
                (user_id, coupon_id, active_marker);

        ALTER TABLE seckill_order DROP INDEX idx_user_coupon;
    ELSEIF object_count <> 1 OR valid_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partial B5 seckill migration';
    END IF;

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
      AND column_name = 'status' AND data_type = 'int'
      AND is_nullable = 'NO' AND CAST(column_default AS CHAR) = '1';
    IF valid_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'seckill_order.status definition mismatch';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'seckill_order'
      AND constraint_name = 'chk_seckill_order_status_b5' AND constraint_type = 'CHECK'
      AND enforced = 'YES';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chk_seckill_order_status_b5 missing';
    END IF;
    SELECT check_clause INTO check_expression
    FROM information_schema.check_constraints
    WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_seckill_order_status_b5';
    SET check_expression = LOWER(REPLACE(REPLACE(REPLACE(check_expression, '`', ''), ' ', ''), '''', ''));
    IF check_expression NOT IN ('statusin(1,2,3)', '(statusin(1,2,3))') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chk_seckill_order_status_b5 definition mismatch';
    END IF;

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE() AND c.table_name = 'seckill_order'
      AND c.column_name = 'active_marker'
      AND c.data_type = 'tinyint'
      AND LOWER(c.column_type) IN ('tinyint', 'tinyint(4)')
      AND c.is_nullable = 'YES'
      AND UPPER(c.extra) = 'STORED GENERATED'
      AND c.generation_expression IS NOT NULL;
    IF valid_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'active_marker definition mismatch';
    END IF;

    SELECT c.generation_expression INTO generation_expression
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE() AND c.table_name = 'seckill_order'
      AND c.column_name = 'active_marker';
    SET generation_expression = LOWER(REPLACE(REPLACE(REPLACE(REPLACE(
        generation_expression, '`', ''), ' ', ''), '(', ''), ')', ''));
    IF generation_expression IS NULL
       OR generation_expression <> 'casewhenstatus=3thennullelse1end' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'active_marker definition mismatch';
    END IF;

    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') INTO new_index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
      AND index_name = 'uk_seckill_order_active_user_coupon';
    SELECT COUNT(*) INTO valid_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
      AND index_name = 'uk_seckill_order_active_user_coupon' AND non_unique = 0;
    IF new_index_columns <> 'user_id,coupon_id,active_marker' OR valid_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'uk_seckill_order_active_user_coupon definition mismatch';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'seckill_order'
      AND index_name = 'idx_user_coupon';
    IF object_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'legacy idx_user_coupon still exists';
    END IF;
END$$
DELIMITER ;

CALL migrate_b5_seckill_state_inventory();
DROP PROCEDURE migrate_b5_seckill_state_inventory;
