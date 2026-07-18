-- 创建地址簿表
CREATE TABLE `address_book` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `consignee` varchar(255) NOT NULL COMMENT '收货人',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `province` varchar(255) NOT NULL COMMENT '省',
  `city` varchar(255) NOT NULL COMMENT '市',
  `district` varchar(255) NOT NULL COMMENT '区',
  `detail` varchar(255) NOT NULL COMMENT '详细地址',
  `postcode` varchar(20) DEFAULT NULL COMMENT '邮编',
  `is_default` int(11) DEFAULT '0' COMMENT '状态 0:默认 1:非默认',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint(20) NOT NULL COMMENT '创建人',
  `update_user` bigint(20) NOT NULL COMMENT '更新人',
  `is_deleted` int(11) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='地址簿';