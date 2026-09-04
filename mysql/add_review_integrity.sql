-- B7 评价资格与 (order_id, product_id) 幂等门禁。
-- 面向 MySQL 8 已有库；不清洗数据，同名索引定义不一致时显式失败。

DROP PROCEDURE IF EXISTS migrate_b7_review_integrity;
DELIMITER $$
CREATE PROCEDURE migrate_b7_review_integrity()
BEGIN
    DECLARE object_count INT DEFAULT 0;
    DECLARE valid_count INT DEFAULT 0;
    DECLARE dirty_count BIGINT DEFAULT 0;
    DECLARE sample_id BIGINT DEFAULT NULL;
    DECLARE index_columns VARCHAR(255) DEFAULT NULL;
    DECLARE diagnostic_message VARCHAR(128);

    SELECT COUNT(*) INTO object_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'review' AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B7 migration requires review table';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name IN ('orders', 'order_detail', 'product')
      AND table_type = 'BASE TABLE';
    IF object_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B7 migration requires orders, order_detail and product tables';
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'review'
      AND column_name IN ('id', 'user_id', 'order_id', 'product_id');
    IF object_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'B7 review columns are missing';
    END IF;

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'review'
      AND column_name IN ('user_id', 'order_id', 'product_id')
      AND data_type = 'bigint' AND is_nullable = 'NO';
    IF valid_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'B7 review references must be BIGINT NOT NULL';
    END IF;

    SELECT COUNT(*), MIN(id) INTO dirty_count, sample_id
    FROM review r
    WHERE r.order_id IS NULL OR r.product_id IS NULL OR r.user_id IS NULL;
    IF dirty_count <> 0 THEN
        SET diagnostic_message = CONCAT('B7 null review references count=', dirty_count,
                                        ', sample_id=', COALESCE(sample_id, 0));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;

    SELECT COUNT(*), MIN(duplicate_id) INTO dirty_count, sample_id
    FROM (
        SELECT MIN(id) AS duplicate_id
        FROM review
        GROUP BY order_id, product_id HAVING COUNT(*) > 1
    ) duplicated;
    IF dirty_count <> 0 THEN
        SET diagnostic_message = CONCAT('B7 duplicate reviews count=', dirty_count,
                                        ', sample_id=', COALESCE(sample_id, 0));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;

    SELECT COUNT(*), MIN(r.id) INTO dirty_count, sample_id
    FROM review r
    LEFT JOIN orders o ON o.id = r.order_id
    WHERE o.id IS NULL;
    IF dirty_count <> 0 THEN
        SET diagnostic_message = CONCAT('B7 orphan review orders count=', dirty_count,
                                        ', sample_id=', COALESCE(sample_id, 0));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;

    SELECT COUNT(*), MIN(r.id) INTO dirty_count, sample_id
    FROM review r
    LEFT JOIN product p ON p.id = r.product_id
    WHERE p.id IS NULL;
    IF dirty_count <> 0 THEN
        SET diagnostic_message = CONCAT('B7 orphan review products count=', dirty_count,
                                        ', sample_id=', COALESCE(sample_id, 0));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;

    SELECT COUNT(*), MIN(r.id) INTO dirty_count, sample_id
    FROM review r
    INNER JOIN orders o ON o.id = r.order_id
    WHERE r.user_id <> o.user_id;
    IF dirty_count <> 0 THEN
        SET diagnostic_message = CONCAT('B7 review owner mismatch count=', dirty_count,
                                        ', sample_id=', COALESCE(sample_id, 0));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;

    SELECT COUNT(*), MIN(r.id) INTO dirty_count, sample_id
    FROM review r
    WHERE NOT EXISTS (
        SELECT 1 FROM order_detail d
        WHERE d.order_id = r.order_id AND d.product_id = r.product_id
    );
    IF dirty_count <> 0 THEN
        SET diagnostic_message = CONCAT('B7 review product not in order count=', dirty_count,
                                        ', sample_id=', COALESCE(sample_id, 0));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;

    SELECT COUNT(*) INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'review'
      AND index_name = 'uk_review_order_product';

    IF object_count = 0 THEN
        ALTER TABLE review
            ADD UNIQUE INDEX uk_review_order_product (order_id, product_id);
    END IF;

    -- Always re-read the final metadata, including the just-created index.  A
    -- successful ALTER is not treated as proof that the required signature exists.
    SELECT COUNT(*),
           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    INTO object_count, index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'review'
      AND index_name = 'uk_review_order_product';

    SELECT COUNT(*) INTO valid_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'review'
      AND index_name = 'uk_review_order_product'
      AND non_unique = 0 AND sub_part IS NULL AND is_visible = 'YES'
      AND index_type = 'BTREE';

    IF object_count <> 2 OR valid_count <> 2 OR index_columns <> 'order_id,product_id' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'uk_review_order_product definition mismatch';
    END IF;
END$$
DELIMITER ;

CALL migrate_b7_review_integrity();
DROP PROCEDURE migrate_b7_review_integrity;
