-- 支付记录表
CREATE TABLE IF NOT EXISTS `payment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `order_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '订单类型 0-普通订单 1-秒杀订单',
  `pay_no` varchar(64) NOT NULL COMMENT '支付流水号',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `pay_method` tinyint(4) DEFAULT '1' COMMENT '支付方式 1微信 2支付宝',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '支付状态 0待支付 1支付中 2成功 3失败',
  `trade_no` varchar(64) DEFAULT NULL COMMENT '支付宝交易号',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';
