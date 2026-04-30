-- 给秒杀活动表添加折扣率字段
-- 执行时间：2026-04-21

ALTER TABLE `seckill_activity`
ADD COLUMN `discount` decimal(3,1) NOT NULL DEFAULT 10.0 COMMENT '折扣率（如9.0表示9折，8.5表示8.5折）' AFTER `name`;

-- 更新现有活动数据
UPDATE `seckill_activity` SET `discount` = 9.0 WHERE `id` = 2;  -- 夏季促销活动 9折
UPDATE `seckill_activity` SET `discount` = 8.5 WHERE `id` = 3;  -- 秋季新品活动 8.5折

-- 验证更新结果
SELECT id, name, discount, start_time, end_time FROM seckill_activity;
