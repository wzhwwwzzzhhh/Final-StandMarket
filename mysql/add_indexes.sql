-- Performance indexes for the existing fashion_shop schema.
-- Safe to rerun on MySQL 8.0: every index is created only when absent.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_index_if_missing $$
CREATE PROCEDURE add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @statement = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD ', p_index_definition
        );
        PREPARE statement FROM @statement;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END $$

-- Product list filters and sort order.
CALL add_index_if_missing('product', 'idx_product_category',
    'INDEX `idx_product_category` (`category_id`, `status`, `sales`)') $$
CALL add_index_if_missing('product', 'idx_product_tag',
    'INDEX `idx_product_tag` (`tag`, `status`, `sales`)') $$

-- User and administration order queries.
CALL add_index_if_missing('orders', 'idx_orders_user_time',
    'INDEX `idx_orders_user_time` (`user_id`, `order_time` DESC)') $$
CALL add_index_if_missing('orders', 'idx_orders_number',
    'UNIQUE INDEX `idx_orders_number` (`number`)') $$
CALL add_index_if_missing('orders', 'idx_orders_status',
    'INDEX `idx_orders_status` (`status`, `order_time` DESC)') $$
CALL add_index_if_missing('order_detail', 'idx_order_detail_order',
    'INDEX `idx_order_detail_order` (`order_id`)') $$

-- Seckill order lookup and active coupon queries.
CALL add_index_if_missing('seckill_order', 'idx_seckill_order_number',
    'UNIQUE INDEX `idx_seckill_order_number` (`order_number`)') $$
CALL add_index_if_missing('seckill_order', 'idx_seckill_order_user',
    'INDEX `idx_seckill_order_user` (`user_id`, `create_time` DESC)') $$
CALL add_index_if_missing('seckill_coupon', 'idx_seckill_coupon_active',
    'INDEX `idx_seckill_coupon_active` (`status`, `start_time`, `end_time`)') $$

-- Per-user data reads and login lookup.
CALL add_index_if_missing('shopping_cart', 'idx_cart_user',
    'INDEX `idx_cart_user` (`user_id`)') $$
CALL add_index_if_missing('address_book', 'idx_address_user',
    'INDEX `idx_address_user` (`user_id`)') $$
CALL add_index_if_missing('user', 'idx_user_phone',
    'UNIQUE INDEX `idx_user_phone` (`phone`)') $$

DROP PROCEDURE IF EXISTS add_index_if_missing $$

DELIMITER ;
