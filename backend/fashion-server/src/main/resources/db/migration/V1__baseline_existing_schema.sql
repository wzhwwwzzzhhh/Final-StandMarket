-- MySQL dump 10.13  Distrib 8.0.34, for Win64 (x86_64)
--
-- Host: localhost    Database: fashion_shop_b10_scratch
-- ------------------------------------------------------
-- Server version	8.0.34

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `address_book`
--

DROP TABLE IF EXISTS `address_book`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `consignee` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '性别',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '手机号',
  `province_code` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认 0 否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_address_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='地址簿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `category`
--

DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int DEFAULT NULL COMMENT '类型   1 商品分类 2 组合商品分类',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `status` int DEFAULT NULL COMMENT '分类状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_category_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='商品及组合商品分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `combination`
--

DROP TABLE IF EXISTS `combination`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `combination` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint NOT NULL COMMENT '商品分类id',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '组合商品名称',
  `price` decimal(10,2) NOT NULL COMMENT '组合商品价格',
  `status` int DEFAULT '1' COMMENT '售卖状态 0:停售 1:起售',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '图片',
  `stock` int DEFAULT '0' COMMENT '库存',
  `sales` int DEFAULT '0' COMMENT '销量',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_combination_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='组合商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `combination_product`
--

DROP TABLE IF EXISTS `combination_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `combination_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `combination_id` bigint DEFAULT NULL COMMENT '组合商品id',
  `product_id` bigint DEFAULT NULL COMMENT '商品id',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '商品名称 （冗余字段）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '商品单价（冗余字段）',
  `copies` int DEFAULT NULL COMMENT '商品份数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=143 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='组合商品关系';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `coupon_template`
--

DROP TABLE IF EXISTS `coupon_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '券名称（如：满100减20）',
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
  `apply_product_ids` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '指定商品id逗号分隔(scope_type=2)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 0停用 1启用',
  `create_time` datetime NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_template_status` (`status`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='优惠券模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `employee`
--

DROP TABLE IF EXISTS `employee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '姓名',
  `username` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '用户名',
  `password` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '密码',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '手机号',
  `sex` varchar(2) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '性别',
  `id_number` varchar(18) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '身份证号',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='员工信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `favorite`
--

DROP TABLE IF EXISTS `favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `create_time` datetime NOT NULL COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_favorite_user_product` (`user_id`,`product_id`),
  KEY `idx_favorite_user` (`user_id`,`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='收藏夹';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `operation_log`
--

DROP TABLE IF EXISTS `operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `employee_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作人姓名',
  `module` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模块',
  `operation` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作描述',
  `method` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'HTTP方法 + 请求URI',
  `params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '请求参数(JSON)',
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作IP',
  `create_time` datetime NOT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_oper_log_time` (`create_time`),
  KEY `idx_oper_log_emp` (`employee_id`),
  KEY `idx_oper_log_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='管理端操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_detail`
--

DROP TABLE IF EXISTS `order_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '名字',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '图片',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `product_id` bigint DEFAULT NULL COMMENT '商品id',
  `combination_id` bigint DEFAULT NULL COMMENT '组合商品id',
  `sku_info` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'SKU信息',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `is_seckill` tinyint(1) DEFAULT '0' COMMENT '是否为秒杀商品 0:否 1:是',
  `seckill_price` decimal(10,2) DEFAULT NULL COMMENT '秒杀价格',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  PRIMARY KEY (`id`),
  KEY `idx_order_detail_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=80 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '订单号',
  `status` int NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待发货 3已发货 4已完成 5已取消 6退款',
  `user_id` bigint NOT NULL COMMENT '下单用户',
  `address_book_id` bigint NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int NOT NULL DEFAULT '1' COMMENT '支付方式 1微信,2支付宝',
  `pay_status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '配送状态  1立即送出  0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `shipping_fee` int DEFAULT NULL COMMENT '运费',
  `seckill_activity_id` bigint DEFAULT NULL COMMENT '秒杀活动id（逻辑外键）',
  `seckill_coupon_id` bigint DEFAULT NULL COMMENT '秒杀券id（逻辑外键）',
  `is_seckill` tinyint(1) DEFAULT '0' COMMENT '是否为秒杀订单 0:否 1:是',
  `seckill_price` decimal(10,2) DEFAULT NULL COMMENT '秒杀价格',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `user_coupon_id` bigint DEFAULT NULL COMMENT '通用优惠券id（逻辑外键，下单锁定）',
  `stock_deducted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '普通订单库存已扣减且尚未回补 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_orders_number` (`number`),
  KEY `idx_orders_user_time` (`user_id`,`order_time` DESC),
  KEY `idx_orders_status` (`status`,`order_time` DESC),
  KEY `idx_orders_pay_status` (`pay_status`,`order_time` DESC),
  KEY `idx_orders_timeout` (`status`,`pay_status`,`order_time`),
  CONSTRAINT `chk_orders_stock_deducted` CHECK ((`stock_deducted` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=43829 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment`
--

DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_type` tinyint NOT NULL DEFAULT '0' COMMENT '订单类型 0-普通订单 1-秒杀订单',
  `pay_no` varchar(64) NOT NULL COMMENT '支付流水号',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `pay_method` tinyint DEFAULT '1' COMMENT '支付方式 1微信 2支付宝',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态 0待支付 1支付中 2成功 3失败',
  `trade_no` varchar(64) DEFAULT NULL COMMENT '支付宝交易号',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `active_order_id` bigint GENERATED ALWAYS AS ((case when (`status` in (0,1)) then `order_id` else NULL end)) STORED COMMENT '活动支付订单ID',
  `active_order_type` tinyint GENERATED ALWAYS AS ((case when (`status` in (0,1)) then `order_type` else NULL end)) STORED COMMENT '活动支付订单类型',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  UNIQUE KEY `uk_payment_active_order` (`active_order_id`,`active_order_type`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product`
--

DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '商品名称',
  `category_id` bigint NOT NULL COMMENT '商品分类id',
  `price` decimal(10,2) DEFAULT NULL COMMENT '商品价格',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '图片',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '描述信息',
  `status` int DEFAULT '1' COMMENT '0 停售 1 起售',
  `stock` int DEFAULT '0' COMMENT '库存',
  `sales` int DEFAULT '0' COMMENT '销量',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `tag` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '商品标签：衣服、裤子、鞋子、配饰',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_product_name` (`name`),
  KEY `idx_product_category` (`category_id`,`status`,`sales`),
  KEY `idx_product_tag` (`tag`,`status`,`sales`),
  KEY `idx_product_create_time` (`create_time` DESC),
  KEY `idx_product_sales` (`status`,`sales` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_catalog_revision`
--

DROP TABLE IF EXISTS `product_catalog_revision`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_catalog_revision` (
  `product_id` bigint unsigned NOT NULL,
  `item_version` bigint unsigned NOT NULL,
  `item_state` varchar(16) NOT NULL,
  `es_locked_by` varchar(64) DEFAULT NULL,
  `es_locked_until` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`product_id`),
  KEY `idx_product_revision_es_lease` (`es_locked_until`,`product_id`),
  CONSTRAINT `chk_product_revision_state` CHECK ((cast(`item_state` as char charset binary) in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE',_utf8mb4'DELETED'))),
  CONSTRAINT `chk_product_revision_version` CHECK ((`item_version` between 1 and 9007199254740991))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_catalog_state`
--

DROP TABLE IF EXISTS `product_catalog_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_catalog_state` (
  `id` tinyint unsigned NOT NULL,
  `list_version` bigint unsigned NOT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_product_catalog_singleton` CHECK ((`id` = 1)),
  CONSTRAINT `chk_product_catalog_version` CHECK ((`list_version` between 1 and 9007199254740991))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_projection_reconcile_run`
--

DROP TABLE IF EXISTS `product_projection_reconcile_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_projection_reconcile_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `mode` varchar(16) NOT NULL,
  `phase` varchar(16) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `cursor_payload` text,
  `scan_count` bigint unsigned NOT NULL DEFAULT '0',
  `drift_count` bigint unsigned NOT NULL DEFAULT '0',
  `repair_count` bigint unsigned NOT NULL DEFAULT '0',
  `clean_verify_count` int unsigned NOT NULL DEFAULT '0',
  `attempt_count` int unsigned NOT NULL DEFAULT '0',
  `next_retry_at` datetime(3) DEFAULT NULL,
  `locked_by` varchar(64) DEFAULT NULL,
  `locked_until` datetime(3) DEFAULT NULL,
  `last_error_summary` varchar(500) DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `active_slot` tinyint GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'PENDING',_utf8mb4'RUNNING',_utf8mb4'RETRY_WAIT')) then 1 else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_reconcile_active` (`active_slot`),
  KEY `idx_product_reconcile_recovery` (`status`,`next_retry_at`,`id`),
  CONSTRAINT `chk_product_reconcile_counts` CHECK (((`clean_verify_count` <= 2) and (`attempt_count` <= 8))),
  CONSTRAINT `chk_product_reconcile_domain` CHECK (((cast(`mode` as char charset binary) in (_utf8mb4'CUTOVER',_utf8mb4'PERIODIC')) and (cast(`phase` as char charset binary) in (_utf8mb4'MYSQL_SCAN',_utf8mb4'ES_SCAN',_utf8mb4'VERIFY')) and (cast(`status` as char charset binary) in (_utf8mb4'PENDING',_utf8mb4'RUNNING',_utf8mb4'RETRY_WAIT',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED_TERMINAL'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_projection_task`
--

DROP TABLE IF EXISTS `product_projection_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_projection_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `target` varchar(10) NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `catalog_version` bigint unsigned NOT NULL,
  `operation` varchar(10) NOT NULL,
  `payload` longtext NOT NULL,
  `payload_sha256` char(64) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int unsigned NOT NULL DEFAULT '0',
  `claim_count` int unsigned NOT NULL DEFAULT '0',
  `repair_count` int unsigned NOT NULL DEFAULT '0',
  `next_retry_at` datetime(3) DEFAULT NULL,
  `locked_by` varchar(64) DEFAULT NULL,
  `locked_until` datetime(3) DEFAULT NULL,
  `last_error_summary` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  `manual_replay_count` int unsigned NOT NULL DEFAULT '0',
  `last_replayed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_projection_fact` (`target`,`product_id`,`catalog_version`),
  KEY `idx_product_projection_recovery` (`target`,`status`,`next_retry_at`,`id`),
  KEY `idx_product_projection_product` (`product_id`,`catalog_version`,`target`),
  CONSTRAINT `chk_product_projection_attempts` CHECK (((`attempt_count` <= 8) and (`claim_count` >= `attempt_count`) and (`repair_count` <= 8))),
  CONSTRAINT `chk_product_projection_domain` CHECK (((cast(`status` as char charset binary) in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'RETRY_WAIT',_utf8mb4'SUCCEEDED',_utf8mb4'SUPERSEDED',_utf8mb4'FAILED_TERMINAL')) and (((cast(`target` as char charset binary) = _utf8mb4'REDIS') and (cast(`operation` as char charset binary) = _utf8mb4'PUBLISH')) or ((cast(`target` as char charset binary) = _utf8mb4'ES') and (cast(`operation` as char charset binary) in (_utf8mb4'UPSERT',_utf8mb4'DELETE')))))),
  CONSTRAINT `chk_product_projection_version` CHECK ((`catalog_version` between 1 and 9007199254740991))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_sku`
--

DROP TABLE IF EXISTS `product_sku`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `sku_name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'SKU名称（如颜色、尺码）',
  `sku_value` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'SKU值',
  `price` decimal(10,2) DEFAULT NULL COMMENT 'SKU价格',
  `stock` int DEFAULT '0' COMMENT 'SKU库存',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=131 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='商品SKU';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `refund`
--

DROP TABLE IF EXISTS `refund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refund` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `order_detail_id` bigint DEFAULT NULL COMMENT '订单详情id（部分退款，预留）',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `refund_no` varchar(50) COLLATE utf8mb3_bin NOT NULL COMMENT '退款单号',
  `reason` varchar(500) COLLATE utf8mb3_bin DEFAULT NULL COMMENT '退款原因',
  `amount` decimal(10,2) NOT NULL COMMENT '退款金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0待审核 1已同意/待外部退款 2退款完成 3已拒绝',
  `order_status` tinyint NOT NULL COMMENT '申请退款时的订单状态（3已发货 4已完成）',
  `audit_opinion` varchar(500) COLLATE utf8mb3_bin DEFAULT NULL COMMENT '审核意见',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款完成时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_refund_no` (`refund_no`),
  UNIQUE KEY `idx_refund_order` (`order_id`),
  KEY `idx_refund_user` (`user_id`),
  CONSTRAINT `chk_refund_order_status_b3` CHECK ((`order_status` in (3,4))),
  CONSTRAINT `chk_refund_status_b3` CHECK ((`status` in (0,1,2,3)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='退款/售后';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review`
--

DROP TABLE IF EXISTS `review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review` (
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
  UNIQUE KEY `uk_review_order_product` (`order_id`,`product_id`),
  KEY `idx_review_product` (`product_id`,`status`,`create_time` DESC),
  KEY `idx_review_user` (`user_id`,`create_time` DESC),
  KEY `idx_review_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品评价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seckill_activity`
--

DROP TABLE IF EXISTS `seckill_activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '活动名称',
  `discount` decimal(3,1) NOT NULL DEFAULT '10.0' COMMENT '折扣率（如9.0表示9折，8.5表示8.5折）',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` int DEFAULT '0' COMMENT '状态 0:未开始 1:进行中 2:已结束',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='秒杀活动';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seckill_compensation_record`
--

DROP TABLE IF EXISTS `seckill_compensation_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_compensation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `compensation_action` varchar(32) NOT NULL,
  `order_number` varchar(50) NOT NULL,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `first_reason` varchar(32) NOT NULL,
  `last_reason` varchar(32) NOT NULL,
  `evidence_mask` bigint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime(3) DEFAULT NULL,
  `locked_by` varchar(128) DEFAULT NULL,
  `locked_until` datetime(3) DEFAULT NULL,
  `last_result` varchar(64) DEFAULT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `redis_applied_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_compensation_action_order` (`compensation_action`,`order_number`),
  KEY `idx_seckill_compensation_recovery` (`status`,`next_retry_at`,`id`),
  KEY `idx_seckill_compensation_coupon` (`coupon_id`,`id`),
  CONSTRAINT `chk_seckill_compensation_attempt` CHECK ((`attempt_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seckill_coupon`
--

DROP TABLE IF EXISTS `seckill_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '券名称',
  `original_price` decimal(10,2) NOT NULL COMMENT '原价',
  `seckill_price` decimal(10,2) NOT NULL COMMENT '秒杀价',
  `stock` int NOT NULL COMMENT '库存',
  `limit_per_user` int NOT NULL DEFAULT '1' COMMENT '每人限购',
  `status` int DEFAULT '1' COMMENT '状态 0:无效 1:有效',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `start_time` datetime DEFAULT NULL COMMENT '起售时间',
  `end_time` datetime DEFAULT NULL COMMENT '停售时间',
  PRIMARY KEY (`id`),
  KEY `idx_seckill_coupon_active` (`status`,`start_time`,`end_time`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='秒杀券';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seckill_message_log`
--

DROP TABLE IF EXISTS `seckill_message_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` varchar(128) NOT NULL,
  `message_type` varchar(32) NOT NULL,
  `publish_purpose` varchar(32) NOT NULL,
  `business_key` varchar(128) NOT NULL,
  `source_message_id` varchar(128) DEFAULT NULL,
  `source_message_id_hash` char(64) DEFAULT NULL,
  `source_message_id_prefix` varchar(64) DEFAULT NULL,
  `body_sha256` char(64) DEFAULT NULL,
  `body_size` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `payload` text NOT NULL,
  `payload_schema_version` int NOT NULL DEFAULT '1',
  `exchange_name` varchar(128) NOT NULL,
  `routing_key` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `dead_letter_status` varchar(16) NOT NULL DEFAULT 'NONE',
  `confirm_status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `returned` tinyint(1) NOT NULL DEFAULT '0',
  `return_reply_code` int DEFAULT NULL,
  `return_reply_text` varchar(255) DEFAULT NULL,
  `current_correlation_id` varchar(160) DEFAULT NULL,
  `publish_attempt` int NOT NULL DEFAULT '0',
  `consume_attempt` int NOT NULL DEFAULT '0',
  `processing_attempt` int DEFAULT NULL,
  `fallback_attempt` int NOT NULL DEFAULT '0',
  `due_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) DEFAULT NULL,
  `locked_by` varchar(128) DEFAULT NULL,
  `locked_until` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `last_error` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `confirmed_at` datetime(3) DEFAULT NULL,
  `consumed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_message_id` (`message_id`),
  UNIQUE KEY `uk_seckill_message_business` (`message_type`,`business_key`),
  KEY `idx_seckill_message_recovery` (`status`,`next_retry_at`,`id`),
  KEY `idx_seckill_message_reconcile` (`coupon_id`,`user_id`,`status`,`id`),
  CONSTRAINT `chk_seckill_message_attempts` CHECK (((`publish_attempt` >= 0) and (`publish_attempt` <= 5) and (`consume_attempt` >= 0) and (`consume_attempt` <= 3) and (`fallback_attempt` >= 0) and (`fallback_attempt` <= 3) and ((`processing_attempt` is null) or ((`processing_attempt` >= 1) and (`processing_attempt` <= 3))) and (((cast(`status` as char charset binary) = _utf8mb3'PROCESSING') and (`processing_attempt` = `consume_attempt`)) or ((cast(`status` as char charset binary) <> _utf8mb3'PROCESSING') and (`processing_attempt` is null))) and (((cast(`status` as char charset binary) = _utf8mb3'PROCESSING') and (`locked_by` is not null) and (`locked_until` is not null)) or ((cast(`status` as char charset binary) <> _utf8mb3'PROCESSING') and (`locked_by` is null) and (`locked_until` is null))) and (`payload_schema_version` > 0))),
  CONSTRAINT `chk_seckill_message_domains` CHECK (((cast(`message_type` as char charset binary) in (_utf8mb3'ORDER_CREATE',_utf8mb3'ORDER_TIMEOUT',_utf8mb3'BUSINESS_DEAD_LETTER',_utf8mb3'INVALID_MESSAGE')) and (cast(`publish_purpose` as char charset binary) in (_utf8mb3'INITIAL',_utf8mb3'CONSUME_RETRY',_utf8mb3'TIMEOUT_RECOVERY',_utf8mb3'TIMEOUT_FALLBACK',_utf8mb3'DEAD_LETTER')) and (cast(`status` as char charset binary) in (_utf8mb3'PREPARED',_utf8mb3'SENT',_utf8mb3'BROKER_ACKED',_utf8mb3'PROCESSING',_utf8mb3'CONSUMED',_utf8mb3'RETRY_PUBLISH_PENDING',_utf8mb3'TIMEOUT_PUBLISH_PENDING',_utf8mb3'TIMEOUT_FALLBACK_PENDING',_utf8mb3'DEAD_LETTER_PUBLISH_PENDING',_utf8mb3'CONSUME_EXHAUSTED',_utf8mb3'COMPENSATION_PENDING',_utf8mb3'COMPENSATED',_utf8mb3'MANUAL_REQUIRED')) and (cast(`dead_letter_status` as char charset binary) in (_utf8mb3'NONE',_utf8mb3'PENDING',_utf8mb3'ACKED',_utf8mb3'MANUAL_REQUIRED')) and (cast(`confirm_status` as char charset binary) in (_utf8mb3'PENDING',_utf8mb3'ACK',_utf8mb3'NACK',_utf8mb3'TIMEOUT')) and (((cast(`message_type` as char charset binary) = _utf8mb3'ORDER_CREATE') and (((cast(`publish_purpose` as char charset binary) = _utf8mb3'INITIAL') and (cast(`status` as char charset binary) in (_utf8mb3'PREPARED',_utf8mb3'SENT',_utf8mb3'BROKER_ACKED',_utf8mb3'PROCESSING',_utf8mb3'CONSUMED',_utf8mb3'COMPENSATION_PENDING',_utf8mb3'COMPENSATED',_utf8mb3'MANUAL_REQUIRED'))) or ((cast(`publish_purpose` as char charset binary) = _utf8mb3'CONSUME_RETRY') and (cast(`status` as char charset binary) in (_utf8mb3'SENT',_utf8mb3'BROKER_ACKED',_utf8mb3'PROCESSING',_utf8mb3'CONSUMED',_utf8mb3'RETRY_PUBLISH_PENDING',_utf8mb3'CONSUME_EXHAUSTED',_utf8mb3'MANUAL_REQUIRED'))))) or ((cast(`message_type` as char charset binary) = _utf8mb3'ORDER_TIMEOUT') and (cast(`publish_purpose` as char charset binary) in (_utf8mb3'TIMEOUT_RECOVERY',_utf8mb3'TIMEOUT_FALLBACK')) and (cast(`status` as char charset binary) in (_utf8mb3'PREPARED',_utf8mb3'SENT',_utf8mb3'BROKER_ACKED',_utf8mb3'PROCESSING',_utf8mb3'CONSUMED',_utf8mb3'TIMEOUT_PUBLISH_PENDING',_utf8mb3'TIMEOUT_FALLBACK_PENDING',_utf8mb3'CONSUME_EXHAUSTED',_utf8mb3'MANUAL_REQUIRED'))) or ((cast(`message_type` as char charset binary) = _utf8mb3'BUSINESS_DEAD_LETTER') and (cast(`publish_purpose` as char charset binary) = _utf8mb3'DEAD_LETTER') and (cast(`status` as char charset binary) in (_utf8mb3'PREPARED',_utf8mb3'SENT',_utf8mb3'BROKER_ACKED',_utf8mb3'DEAD_LETTER_PUBLISH_PENDING',_utf8mb3'MANUAL_REQUIRED'))) or ((cast(`message_type` as char charset binary) = _utf8mb3'INVALID_MESSAGE') and (cast(`publish_purpose` as char charset binary) = _utf8mb3'DEAD_LETTER') and (cast(`status` as char charset binary) = _utf8mb3'CONSUME_EXHAUSTED')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seckill_order`
--

DROP TABLE IF EXISTS `seckill_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `coupon_id` bigint NOT NULL COMMENT '秒杀券id',
  `order_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '订单号',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 1:待支付 2:已支付 3:已取消',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `active_marker` tinyint GENERATED ALWAYS AS ((case when (`status` = 3) then NULL else 1 end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_seckill_order_number` (`order_number`),
  UNIQUE KEY `uk_seckill_order_active_user_coupon` (`user_id`,`coupon_id`,`active_marker`),
  KEY `idx_seckill_order_user` (`user_id`,`create_time` DESC),
  KEY `idx_sorder_status` (`status`),
  CONSTRAINT `chk_seckill_order_status_b5` CHECK ((`status` in (1,2,3)))
) ENGINE=InnoDB AUTO_INCREMENT=44475 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='秒杀订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seckill_reconciliation_anomaly`
--

DROP TABLE IF EXISTS `seckill_reconciliation_anomaly`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_reconciliation_anomaly` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anomaly_type` varchar(32) NOT NULL,
  `coupon_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'OPEN',
  `occurrence_count` int NOT NULL DEFAULT '1',
  `clean_scan_count` int NOT NULL DEFAULT '0',
  `sample_user_id` bigint DEFAULT NULL,
  `sample_order_number` varchar(50) DEFAULT NULL,
  `details_hash` char(64) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `first_seen_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_seen_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `resolved_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_anomaly_type_coupon` (`anomaly_type`,`coupon_id`),
  KEY `idx_seckill_anomaly_status` (`status`,`last_seen_at`,`id`),
  CONSTRAINT `chk_seckill_anomaly_counts` CHECK (((`occurrence_count` > 0) and (`clean_scan_count` >= 0) and (`coupon_id` >= 0) and (((`coupon_id` = 0) and (cast(`anomaly_type` as char charset binary) = _utf8mb3'INVALID_REGISTRY_MEMBER')) or ((`coupon_id` > 0) and (cast(`anomaly_type` as char charset binary) <> _utf8mb3'INVALID_REGISTRY_MEMBER')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shopping_cart`
--

DROP TABLE IF EXISTS `shopping_cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '商品名称',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '图片',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint DEFAULT NULL COMMENT '商品id',
  `combination_id` bigint DEFAULT NULL COMMENT '组合商品id',
  `sku_info` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'SKU信息',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_cart_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=115 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='购物车';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `special_offer`
--

DROP TABLE IF EXISTS `special_offer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `special_offer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `original_price` decimal(10,2) NOT NULL COMMENT '原价',
  `offer_price` decimal(10,2) NOT NULL COMMENT '特价',
  `stock` int NOT NULL COMMENT '库存',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` int DEFAULT '0' COMMENT '状态 0:未开始 1:进行中 2:已结束',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='特价商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` varchar(45) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '微信用户唯一标识',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '姓名',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '性别',
  `id_number` varchar(18) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '头像',
  `create_time` datetime DEFAULT NULL,
  `password` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '密码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=148 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='用户信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_coupon`
--

DROP TABLE IF EXISTS `user_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_coupon` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-05 12:00:39
