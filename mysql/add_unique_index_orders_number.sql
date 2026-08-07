-- 订单号唯一性加固：为 orders.number 补充唯一索引
-- 执行时间：2026-08-07
-- 执行环境：MySQL 8.0，开发/生产 orders 表所在实例。final07.sql 建表已含该索引，本脚本
--          仅用于早期建库（索引缺失）的环境做增量补充，可重复执行。
-- 说明：number 为 NULL 的多行在 MySQL 唯一索引下不冲突，可安全加索引；
--       若存在非 NULL 重复历史订单号，执行前请先清理。

SET @db_name = DATABASE();
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'orders'
      AND index_name = 'idx_orders_number'
);

SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `orders` ADD UNIQUE KEY `idx_orders_number` (`number`)',
    'SELECT ''index already exists'' AS notice');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证
SHOW INDEX FROM `orders` WHERE Key_name = 'idx_orders_number';
