-- B1: ensure at most one active payment (status 0/1) per order and order type.
-- Stop application payment writes before running this migration on an existing database.
-- The script never chooses or deletes conflicting historical rows.

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_payment_active_unique$$
CREATE PROCEDURE migrate_payment_active_unique()
BEGIN
    DECLARE active_order_id_columns INT DEFAULT 0;
    DECLARE active_order_type_columns INT DEFAULT 0;
    DECLARE valid_active_order_id_columns INT DEFAULT 0;
    DECLARE valid_active_order_type_columns INT DEFAULT 0;
    DECLARE target_index_rows INT DEFAULT 0;
    DECLARE valid_index_rows INT DEFAULT 0;
    DECLARE duplicate_groups INT DEFAULT 0;

    SELECT COUNT(*) INTO active_order_id_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'payment'
       AND column_name = 'active_order_id';

    SELECT COUNT(*) INTO active_order_type_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'payment'
       AND column_name = 'active_order_type';

    SELECT COUNT(*) INTO valid_active_order_id_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'payment'
       AND column_name = 'active_order_id'
       AND data_type = 'bigint'
       AND UPPER(extra) = 'STORED GENERATED'
       AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
             generation_expression, '`', ''), ' ', ''), '(', ''), ')', ''), CHAR(10), ''))
           = 'casewhenstatusin0,1thenorder_idelsenullend';

    SELECT COUNT(*) INTO valid_active_order_type_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'payment'
       AND column_name = 'active_order_type'
       AND data_type = 'tinyint'
       AND UPPER(extra) = 'STORED GENERATED'
       AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
             generation_expression, '`', ''), ' ', ''), '(', ''), ')', ''), CHAR(10), ''))
           = 'casewhenstatusin0,1thenorder_typeelsenullend';

    SELECT COUNT(*),
           COALESCE(SUM(
             CASE
               WHEN non_unique = 0
                AND ((seq_in_index = 1 AND column_name = 'active_order_id')
                  OR (seq_in_index = 2 AND column_name = 'active_order_type'))
               THEN 1 ELSE 0
             END
           ), 0)
      INTO target_index_rows, valid_index_rows
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'payment'
       AND index_name = 'uk_payment_active_order';

    IF active_order_id_columns = 1
       AND active_order_type_columns = 1
       AND valid_active_order_id_columns = 1
       AND valid_active_order_type_columns = 1
       AND target_index_rows = 2
       AND valid_index_rows = 2 THEN
        SELECT 'B1 payment active uniqueness already applied' AS migration_status;
    ELSEIF active_order_id_columns <> 0
       OR active_order_type_columns <> 0
       OR target_index_rows <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'partial B1 payment active uniqueness schema detected; manual review required';
    ELSE
        SELECT COUNT(*) INTO duplicate_groups
          FROM (
                SELECT order_id, order_type
                  FROM payment
                 WHERE status IN (0, 1)
                 GROUP BY order_id, order_type
                HAVING COUNT(*) > 1
               ) AS conflicts;

        IF duplicate_groups > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'duplicate active payment rows detected; reconcile manually before retry';
        END IF;

        ALTER TABLE payment
          ADD COLUMN active_order_id BIGINT
            GENERATED ALWAYS AS (CASE WHEN status IN (0, 1) THEN order_id ELSE NULL END) STORED,
          ADD COLUMN active_order_type TINYINT
            GENERATED ALWAYS AS (CASE WHEN status IN (0, 1) THEN order_type ELSE NULL END) STORED,
          ADD UNIQUE INDEX uk_payment_active_order (active_order_id, active_order_type);

        SELECT 'B1 payment active uniqueness applied' AS migration_status;
    END IF;
END$$

CALL migrate_payment_active_unique()$$
DROP PROCEDURE migrate_payment_active_unique$$

DELIMITER ;
