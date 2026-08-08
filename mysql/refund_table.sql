-- Phase 5: 退款/售后表
-- 执行: source mysql/refund_table.sql;
-- 或者直接在 fashion_shop 数据库中执行

CREATE TABLE IF NOT EXISTS `refund` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `order_detail_id` bigint DEFAULT NULL COMMENT '订单详情id（部分退款，预留）',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `refund_no` varchar(50) NOT NULL COMMENT '退款单号',
  `reason` varchar(500) DEFAULT NULL COMMENT '退款原因',
  `amount` decimal(10,2) NOT NULL COMMENT '退款金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0待审核 2已退款 3拒绝',
  `order_status` tinyint DEFAULT NULL COMMENT '申请退款时的订单状态（拒绝时恢复用）',
  `audit_opinion` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款完成时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_refund_no` (`refund_no`),
  UNIQUE KEY `idx_refund_order` (`order_id`),
  KEY `idx_refund_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='退款/售后';
