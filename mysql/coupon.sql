-- ============================================================
-- 通用优惠券系统建表脚本（阶段E）
-- 说明：本脚本幂等（CREATE TABLE IF NOT EXISTS），可重复执行。
-- 注意：orders 表新增 user_coupon_id 列需要手动确认（MySQL 不支持
--       ALTER TABLE ... ADD COLUMN IF NOT EXISTS），若列已存在请跳过末尾语句。
-- ============================================================

-- 优惠券模板（管理端创建）
CREATE TABLE IF NOT EXISTS `coupon_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '券名称（如：满100减20）',
  `type` tinyint NOT NULL COMMENT '类型 1满减 2折扣 3现金',
  `threshold` decimal(10,2) DEFAULT NULL COMMENT '使用门槛（满X元可用，0=无门槛）',
  `discount` decimal(10,2) DEFAULT NULL COMMENT '满减金额/现金金额，或折扣值(如8.5=85折)',
  `total_count` int DEFAULT '0' COMMENT '发行总量 0=不限量',
  `per_user_limit` int DEFAULT '1' COMMENT '每人限领',
  `valid_type` tinyint NOT NULL COMMENT '有效期类型 1固定时间 2领取后N天',
  `valid_days` int DEFAULT NULL COMMENT '领取后有效天数(valid_type=2)',
  `start_time` datetime DEFAULT NULL COMMENT '有效开始(valid_type=1)',
  `end_time` datetime DEFAULT NULL COMMENT '有效结束(valid_type=1)',
  `scope_type` tinyint NOT NULL DEFAULT '0' COMMENT '适用范围 0全店 1指定分类 2指定商品',
  `apply_category_id` bigint DEFAULT NULL COMMENT '指定分类id(scope_type=1)',
  `apply_product_ids` varchar(500) DEFAULT NULL COMMENT '指定商品id逗号分隔(scope_type=2)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 0停用 1启用',
  `create_time` datetime NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_template_status` (`status`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='优惠券模板';

-- 用户持有券
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `template_id` bigint NOT NULL COMMENT '模板id',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0未使用 1已使用 2已过期 3已锁定(下单核销中)',
  `obtain_time` datetime NOT NULL COMMENT '领取时间',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `use_order_id` bigint DEFAULT NULL COMMENT '核销订单id',
  `use_time` datetime DEFAULT NULL COMMENT '核销时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_coupon_user_status` (`user_id`,`status`),
  KEY `idx_user_coupon_template` (`template_id`),
  KEY `idx_user_coupon_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户优惠券';

-- ============================================================
-- orders 表升级：记录订单使用的通用优惠券 id（下单锁券后回填，取消/支付回调按它释放/核销）
-- 已执行过请忽略；执行一次即可。
-- ============================================================
-- ALTER TABLE `orders` ADD COLUMN `user_coupon_id` bigint DEFAULT NULL COMMENT '通用优惠券id（逻辑外键，下单锁定）' AFTER `original_price`;
-- ALTER TABLE `orders` ADD INDEX `idx_orders_user_coupon` (`user_coupon_id`);
