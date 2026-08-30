-- B2 普通订单库存事实与超时扫描索引。
-- 可在 MySQL 8 已有库重复执行；同名对象定义不一致时显式失败。

DROP PROCEDURE IF EXISTS migrate_b2_order_inventory_state;
DELIMITER $$
CREATE PROCEDURE migrate_b2_order_inventory_state()
BEGIN
    DECLARE object_count INT DEFAULT 0;
    DECLARE valid_count INT DEFAULT 0;
    DECLARE index_columns VARCHAR(255);
    DECLARE check_expression TEXT;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'orders';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B2 migration requires orders table';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'user_coupon_id';
    IF object_count = 0 THEN
        ALTER TABLE orders ADD COLUMN user_coupon_id BIGINT NULL
            COMMENT '通用优惠券id（逻辑外键，下单锁定）' AFTER original_price;
    ELSE
        SELECT COUNT(*) INTO valid_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'user_coupon_id'
          AND data_type = 'bigint' AND is_nullable = 'YES' AND column_default IS NULL;
        IF valid_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'orders.user_coupon_id definition mismatch';
        END IF;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'stock_deducted';
    IF object_count = 0 THEN
        ALTER TABLE orders ADD COLUMN stock_deducted TINYINT(1) NOT NULL DEFAULT 0
            COMMENT '普通订单库存已扣减且尚未回补 0否 1是' AFTER user_coupon_id;
    ELSE
        SELECT COUNT(*) INTO valid_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'stock_deducted'
          AND column_type = 'tinyint(1)' AND is_nullable = 'NO'
          AND CAST(column_default AS CHAR) = '0';
        IF valid_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'orders.stock_deducted definition mismatch';
        END IF;
    END IF;

    -- 历史履约中订单不能伪造库存事实；必须先在旧版本完成履约再切换 B2。
    SELECT COUNT(*) INTO valid_count
    FROM orders
    WHERE status IN (2, 3) AND stock_deducted = 0;
    IF valid_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'historical fulfillment orders must be completed before B2 migration';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'orders'
      AND constraint_name = 'chk_orders_stock_deducted' AND constraint_type = 'CHECK';
    IF object_count = 0 THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_stock_deducted
            CHECK (stock_deducted IN (0, 1));
    ELSE
        SELECT check_clause INTO check_expression
        FROM information_schema.check_constraints
        WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_orders_stock_deducted';
        SET check_expression = LOWER(REPLACE(REPLACE(REPLACE(REPLACE(check_expression, '`', ''), ' ', ''), '(', ''), ')', ''));
        IF check_expression <> 'stock_deductedin0,1' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chk_orders_stock_deducted definition mismatch';
        END IF;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_timeout';
    IF object_count = 0 THEN
        ALTER TABLE orders ADD INDEX idx_orders_timeout (status, pay_status, order_time);
    ELSE
        SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_timeout';
        SELECT COUNT(*) INTO valid_count
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_timeout'
          AND non_unique = 1;
        IF index_columns <> 'status,pay_status,order_time' OR valid_count <> 3 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'idx_orders_timeout definition mismatch';
        END IF;
    END IF;
END$$
DELIMITER ;

CALL migrate_b2_order_inventory_state();
DROP PROCEDURE migrate_b2_order_inventory_state;
