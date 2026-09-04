-- Table structure for table `review`
-- 商品评价表

CREATE TABLE IF NOT EXISTS `review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `rating` tinyint NOT NULL COMMENT '评分 1-5',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '评价内容',
  `images` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '评价图片(JSON数组)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 1显示 0隐藏',
  `create_time` datetime(3) NOT NULL COMMENT '评价时间',
  `update_time` datetime(3) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_product` (`product_id`, `status`, `create_time` DESC),
  KEY `idx_review_user` (`user_id`, `create_time` DESC),
  KEY `idx_review_order` (`order_id`),
  UNIQUE KEY `uk_review_order_product` (`order_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品评价';
