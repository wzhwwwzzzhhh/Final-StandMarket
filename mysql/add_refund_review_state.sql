-- B3 退款审核真实状态与可恢复申请前状态。
-- MySQL 8 已有库增量脚本：只收敛 schema，不自动改写任何退款、订单、支付或库存事实。

DROP PROCEDURE IF EXISTS migrate_b3_refund_review_state;
DELIMITER $$
CREATE PROCEDURE migrate_b3_refund_review_state()
BEGIN
    DECLARE object_count INT DEFAULT 0;
    DECLARE valid_count INT DEFAULT 0;
    DECLARE status_marker_count INT DEFAULT 0;
    DECLARE order_marker_count INT DEFAULT 0;
    DECLARE status_check_expression TEXT;
    DECLARE order_check_expression TEXT;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'refund';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B3 migration requires refund table';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'orders';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B3 migration requires orders table';
    END IF;

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refund' AND column_name = 'status'
      AND data_type = 'tinyint' AND is_nullable = 'NO'
      AND CAST(column_default AS CHAR) = '0';
    IF valid_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'refund.status definition mismatch';
    END IF;

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refund' AND column_name = 'order_status'
      AND data_type = 'tinyint';
    IF valid_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'refund.order_status definition mismatch';
    END IF;

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refund'
      AND column_name IN ('order_id', 'user_id', 'refund_time');
    IF valid_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'refund B3 columns are incomplete';
    END IF;

    SELECT COUNT(*) INTO status_marker_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'refund'
      AND constraint_name = 'chk_refund_status_b3';
    SELECT COUNT(*) INTO order_marker_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'refund'
      AND constraint_name = 'chk_refund_order_status_b3';

    IF status_marker_count <> order_marker_count THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partial B3 refund migration';
    END IF;
    IF status_marker_count > 1 OR order_marker_count > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'duplicate B3 refund marker';
    END IF;

    IF status_marker_count = 0 THEN
        SELECT COUNT(*) INTO valid_count
        FROM refund
        WHERE status IS NULL OR status NOT IN (0, 1, 2, 3);
        IF valid_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'refund contains unsupported status';
        END IF;

        -- 旧系统从未产生可信状态 1/2；必须先人工核对支付渠道、退款流水和库存事实。
        SELECT COUNT(*) INTO valid_count
        FROM refund
        WHERE status IN (1, 2);
        IF valid_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'historical refund status 1 or 2 requires reconciliation';
        END IF;

        SELECT COUNT(*) INTO valid_count
        FROM refund
        WHERE status IN (0, 1, 3) AND refund_time IS NOT NULL;
        IF valid_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'non-completed refund has refund_time';
        END IF;

        SELECT COUNT(*) INTO valid_count
        FROM refund
        WHERE order_status IS NULL OR order_status NOT IN (3, 4);
        IF valid_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'refund.order_status requires reconciliation';
        END IF;

        SELECT COUNT(*) INTO valid_count
        FROM refund r
        LEFT JOIN orders o ON o.id = r.order_id
        WHERE r.status = 0
          AND (o.id IS NULL OR o.user_id <> r.user_id OR o.status <> 6);
        IF valid_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'pending refund cannot be safely rejected';
        END IF;

        SELECT COUNT(*) INTO valid_count
        FROM refund r
        LEFT JOIN orders o ON o.id = r.order_id
        WHERE r.status = 3
          AND (o.id IS NULL OR o.user_id <> r.user_id
               OR (r.order_status = 3 AND o.status NOT IN (3, 4))
               OR (r.order_status = 4 AND o.status <> 4));
        IF valid_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rejected refund order state requires reconciliation';
        END IF;

        ALTER TABLE refund
            MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
                COMMENT '状态 0待审核 1已同意/待外部退款 2退款完成 3已拒绝',
            MODIFY COLUMN order_status TINYINT NOT NULL
                COMMENT '申请退款时的订单状态（3已发货 4已完成）',
            ADD CONSTRAINT chk_refund_status_b3
                CHECK (status IN (0, 1, 2, 3)) ENFORCED,
            ADD CONSTRAINT chk_refund_order_status_b3
                CHECK (order_status IN (3, 4)) ENFORCED;
    ELSE
        SELECT COUNT(*) INTO valid_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'refund' AND column_name = 'order_status'
          AND data_type = 'tinyint' AND is_nullable = 'NO';
        IF valid_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'refund.order_status definition mismatch';
        END IF;

        SELECT COUNT(*) INTO valid_count
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'refund'
          AND constraint_name IN ('chk_refund_status_b3', 'chk_refund_order_status_b3')
          AND constraint_type = 'CHECK' AND enforced = 'YES';
        IF valid_count <> 2 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B3 refund CHECK marker mismatch';
        END IF;

        SELECT check_clause INTO status_check_expression
        FROM information_schema.check_constraints
        WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_refund_status_b3';
        SELECT check_clause INTO order_check_expression
        FROM information_schema.check_constraints
        WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_refund_order_status_b3';
        -- 保留 IN 列表的括号和逗号，避免 (0,12,3) 冒充 (0,1,2,3)。
        SET status_check_expression = LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            status_check_expression, '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''));
        SET order_check_expression = LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            order_check_expression, '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''));
        IF status_check_expression NOT IN ('statusin(0,1,2,3)', '(statusin(0,1,2,3))') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chk_refund_status_b3 definition mismatch';
        END IF;
        IF order_check_expression NOT IN ('order_statusin(3,4)', '(order_statusin(3,4))') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chk_refund_order_status_b3 definition mismatch';
        END IF;

        SELECT COUNT(*) INTO valid_count
        FROM refund
        WHERE status IN (0, 1, 3) AND refund_time IS NOT NULL;
        IF valid_count <> 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'non-completed refund has refund_time';
        END IF;
    END IF;
END$$
DELIMITER ;

CALL migrate_b3_refund_review_state();
DROP PROCEDURE migrate_b3_refund_review_state;
