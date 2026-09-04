-- MySQL dump 10.16  Distrib 10.1.23-MariaDB, for Win64 (AMD64)
--
-- Host: 127.0.0.1    Database: fashion_shop
-- ------------------------------------------------------
-- Server version	8.0.34

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
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
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `address_book`
--

LOCK TABLES `address_book` WRITE;
/*!40000 ALTER TABLE `address_book` DISABLE KEYS */;
INSERT INTO `address_book` VALUES (14,20,'张三','','12356235623','310000','上海市','310100','上海市','310101','黄浦区','大西街','',1),(15,20,'张三的','','15623562356','110000','北京市','110100','北京市','110102','西城区','空间小','',0);
/*!40000 ALTER TABLE `address_book` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `category`
--

DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `category`
--

LOCK TABLES `category` WRITE;
/*!40000 ALTER TABLE `category` DISABLE KEYS */;
INSERT INTO `category` VALUES (1,1,'上衣',1,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(2,1,'外套',2,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(3,1,'裙子',3,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(4,1,'牛仔裤',4,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(5,1,'休闲裤',5,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(6,1,'运动鞋',6,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(7,1,'皮鞋',7,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(8,1,'帽子',8,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(9,1,'包包',9,1,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1),(10,1,'手表',10,0,'2026-04-14 20:03:49','2026-04-14 20:03:49',1,1);
/*!40000 ALTER TABLE `category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `combination`
--

DROP TABLE IF EXISTS `combination`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `combination`
--

LOCK TABLES `combination` WRITE;
/*!40000 ALTER TABLE `combination` DISABLE KEYS */;
/*!40000 ALTER TABLE `combination` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `combination_product`
--

DROP TABLE IF EXISTS `combination_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `combination_product`
--

LOCK TABLES `combination_product` WRITE;
/*!40000 ALTER TABLE `combination_product` DISABLE KEYS */;
/*!40000 ALTER TABLE `combination_product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `employee`
--

DROP TABLE IF EXISTS `employee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `employee`
--

LOCK TABLES `employee` WRITE;
/*!40000 ALTER TABLE `employee` DISABLE KEYS */;
INSERT INTO `employee` VALUES (6,'管理员','admin','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW','13800138000','男','110101199001011234',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(7,'张三','zhangsan','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW','13800138001','男','110101199001011235',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(8,'李四','lisi','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW','13800138002','女','110101199001011236',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(9,'王五','wangwu','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW','13800138003','男','110101199001011237',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(10,'赵六','zhaoliu','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW','13800138004','女','110101199001011238',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1);
/*!40000 ALTER TABLE `employee` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_detail`
--

DROP TABLE IF EXISTS `order_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `order_detail`
--

LOCK TABLES `order_detail` WRITE;
/*!40000 ALTER TABLE `order_detail` DISABLE KEYS */;
INSERT INTO `order_detail` VALUES (71,'围巾','https://picsum.photos/200/300?random=38',43825,138,NULL,NULL,1,99.00,0,NULL,NULL),(72,'袜子','https://picsum.photos/200/300?random=39',43825,139,NULL,NULL,1,19.00,0,NULL,NULL),(73,'太阳镜','https://picsum.photos/200/300?random=40',43826,140,NULL,NULL,1,159.00,0,NULL,NULL),(74,'石英表','https://picsum.photos/200/300?random=35',43826,135,NULL,NULL,1,699.00,0,NULL,NULL),(75,'单肩包','https://picsum.photos/200/300?random=32',43826,132,NULL,NULL,1,189.00,0,NULL,NULL),(76,'运动发带','https://picsum.photos/200/300?random=36',43827,136,NULL,'默认规格',1,29.00,0,NULL,NULL),(77,'袜子','https://picsum.photos/200/300?random=39',43827,139,NULL,'默认规格',1,19.00,0,NULL,NULL),(78,'机械手表','https://picsum.photos/200/300?random=33',43827,133,NULL,'默认规格',1,1299.00,0,NULL,NULL),(79,'袜子','https://picsum.photos/200/300?random=39',43828,139,NULL,'XL',4,76.00,0,NULL,NULL);
/*!40000 ALTER TABLE `order_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (43825,'ORD1776833285875',1,20,14,'2026-04-22 12:48:06',NULL,1,0,118.00,NULL,'12356235623','上海市上海市黄浦区大西街',NULL,'张三',NULL,NULL,NULL,NULL,1,NULL,0,NULL,NULL,0,NULL,NULL,NULL,0),(43826,'ORD1776833347541',1,20,14,'2026-04-22 12:49:08',NULL,1,0,1047.00,NULL,'12356235623','上海市上海市黄浦区大西街',NULL,'张三',NULL,NULL,NULL,NULL,1,NULL,0,NULL,NULL,0,NULL,NULL,NULL,0),(43827,'ORD1776834118072',1,20,14,'2026-04-22 13:01:58',NULL,1,0,1347.00,NULL,'12356235623','上海市上海市黄浦区大西街',NULL,'张三',NULL,NULL,NULL,NULL,1,NULL,0,NULL,NULL,0,NULL,NULL,NULL,0),(43828,'ORD1776835169460',1,20,14,'2026-04-22 13:19:29',NULL,1,0,76.00,NULL,'12356235623','上海市上海市黄浦区大西街',NULL,'张三',NULL,NULL,NULL,NULL,1,NULL,0,NULL,NULL,0,NULL,NULL,NULL,0);
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product`
--

DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  `tag` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT '商品标签：衣服、裤子、鞋子、配饰',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_product_name` (`name`),
  KEY `idx_product_category` (`category_id`,`status`,`sales`),
  KEY `idx_product_tag` (`tag`,`status`,`sales`),
  KEY `idx_product_create_time` (`create_time` DESC),
  KEY `idx_product_sales` (`status`,`sales` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product`
--

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` VALUES (1,'纯棉T恤',1,99.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cotton%20t-shirt&image_size=square','舒适纯棉面料，透气性好',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(12,'长袖衬衫',1,199.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/956ea2ec-54b6-4e9a-9406-0ef51bd41116.png','商务休闲衬衫，面料舒适',1,80,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(13,'短袖衬衫',1,149.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/5ca78059-20f8-440a-9ee5-131499753c27.png','夏季短袖衬衫，清凉透气',1,90,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(14,'连帽卫衣',1,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=hoodie&image_size=square','时尚连帽卫衣，保暖舒适',1,70,25,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(15,'针织衫',1,169.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/c774de97-6332-49ba-99c1-ec3f561e92de.png','柔软针织衫，适合春秋季节',1,85,35,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(16,'牛仔外套',2,299.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=denim%20jacket&image_size=square','经典牛仔外套，百搭时尚',1,50,20,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(17,'休闲外套',2,249.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=casual%20jacket&image_size=square','轻便休闲外套，日常穿搭',1,60,25,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(18,'西装外套',2,499.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/2cad2073-ee0a-40c3-8530-b38561f4dcd8.png','商务西装外套，正式场合必备',1,40,15,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(19,'羽绒服',2,599.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=down%20jacket&image_size=square','冬季保暖羽绒服，轻便舒适',1,30,10,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(20,'连衣裙',3,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=dress&image_size=square','优雅连衣裙，适合各种场合',1,80,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(21,'半身裙',3,149.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/0a09f822-6f7a-45c5-9b35-83d183c8da23.png','时尚半身裙，百搭单品',1,90,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(22,'碎花裙',3,249.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=floral%20dress&image_size=square','清新碎花裙，夏季必备',1,70,35,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(23,'牛仔裤',4,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=jeans&image_size=square','修身牛仔裤，舒适耐穿',1,120,60,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(24,'破洞牛仔裤',4,199.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/8e5d426f-69ca-4905-b1a6-3cc4d4ad4c1d.png','时尚破洞牛仔裤，潮流单品',1,80,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(25,'直筒牛仔裤',4,169.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/c142c459-32b6-4804-bdcf-37cb9a2f69d0.png','经典直筒牛仔裤，百搭款式',1,90,45,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(26,'休闲裤',5,129.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=casual%20pants&image_size=square','宽松休闲裤，日常百搭',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(27,'运动裤',5,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sports%20pants&image_size=square','透气运动裤，适合健身',1,110,55,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(28,'西裤',5,249.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/489f37d7-0c84-4545-8db5-77c5cf8d6fa1.png','商务西裤，正式场合必备',1,70,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(33,'智能手表',10,899.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smart%20watch&image_size=square','多功能智能手表，科技感十足',1,40,15,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(34,'手表',10,599.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=watch&image_size=square','时尚手表，提升品味',1,30,10,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(35,'斜挎包',9,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=crossbody%20bag&image_size=square','时尚斜挎包，方便携带',1,80,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(36,'钱包',9,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=wallet&image_size=square','精致钱包，收纳有序',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(45,'跑步鞋',6,349.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=running%20shoes&image_size=square','减震跑步鞋，舒适透气',1,70,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(46,'休闲皮鞋',7,399.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=casual%20leather%20shoes&image_size=square','时尚休闲皮鞋，日常穿搭',1,50,20,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(47,'靴子',7,599.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=boots&image_size=square','冬季靴子，保暖时尚',1,30,10,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(48,'棒球帽',8,59.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=baseball%20cap&image_size=square','时尚棒球帽，防晒必备',1,150,80,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(49,'渔夫帽',8,79.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=bucket%20hat&image_size=square','潮流渔夫帽，个性十足',1,120,60,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(50,'毛线帽',8,89.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/99c88f80-40fe-401b-9575-6d20e4dcae74.png','冬季毛线帽，保暖舒适',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(51,'手提包',9,299.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=handbag&image_size=square','大容量手提包，实用美观',1,70,35,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(56,'皮鞋',7,499.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/1aa36c16-5366-4920-a8bb-2d7f83d4f7e9.png','商务皮鞋，正式场合必备',1,40,15,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(57,'篮球鞋',6,499.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=basketball%20shoes&image_size=square','专业篮球鞋，支撑性好',1,50,20,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(96,'运动鞋',6,399.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sports%20shoes&image_size=square','轻便运动鞋，适合跑步',1,60,25,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(99,'超级安全衣',1,500.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/4c5ac936-61b1-475f-82f4-f7d14883b5a0.png','超级好看的安全衣',1,20,NULL,NULL,NULL,NULL,NULL,'衣服'),(100,'小兔子',10,123.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/3d7972f7-582c-4d04-9c03-7198bd6bac55.jpg','兔子手表',1,55,NULL,NULL,NULL,NULL,NULL,'配饰'),(101,'圆领T恤',1,89.00,'https://java00001-ai.oss-cn-beijing.aliyuncs.com/652f79e6-dc53-4fce-b103-4a7a1202c6a9.png','纯棉圆领，日常休闲',1,120,45,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(102,'V领T恤',1,99.00,'https://picsum.photos/200/300?random=2','V领设计，显瘦百搭',1,110,38,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(103,'POLO衫',1,159.00,'https://picsum.photos/200/300?random=3','经典POLO，商务休闲',1,80,28,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(104,'亨利衫',1,139.00,'https://picsum.photos/200/300?random=4','亨利领，复古风格',1,70,22,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(105,'长袖T恤',1,109.00,'https://picsum.photos/200/300?random=5','长袖纯棉，春秋必备',1,100,35,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(106,'圆领卫衣',1,189.00,'https://picsum.photos/200/300?random=6','加绒圆领卫衣，舒适保暖',1,90,30,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(107,'风衣',2,399.00,'https://picsum.photos/200/300?random=7','中长款风衣，气质百搭',1,40,12,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(108,'皮夹克',2,599.00,'https://picsum.photos/200/300?random=8','PU皮夹克，机车风格',1,35,10,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(109,'棉服',2,349.00,'https://picsum.photos/200/300?random=9','轻薄棉服，保暖不臃肿',1,50,18,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(110,'冲锋衣',2,459.00,'https://picsum.photos/200/300?random=10','防风防水，户外运动',1,45,15,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(111,'A字裙',3,129.00,'https://picsum.photos/200/300?random=11','高腰A字裙，显瘦遮胯',1,95,42,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(112,'百褶裙',3,139.00,'https://picsum.photos/200/300?random=12','学院风百褶裙，青春活力',1,85,38,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(113,'包臀裙',3,159.00,'https://picsum.photos/200/300?random=13','弹力包臀裙，性感优雅',1,70,25,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(114,'长裙',3,199.00,'https://picsum.photos/200/300?random=14','雪纺长裙，飘逸仙女',1,60,20,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'衣服'),(115,'紧身牛仔裤',4,169.00,'https://picsum.photos/200/300?random=15','弹力紧身，塑形显瘦',1,110,55,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'裤子'),(116,'阔腿牛仔裤',4,189.00,'https://picsum.photos/200/300?random=16','宽松阔腿，复古潮流',1,90,40,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'裤子'),(117,'九分牛仔裤',4,159.00,'https://picsum.photos/200/300?random=17','九分长度，露出脚踝',1,100,48,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'裤子'),(118,'卡其裤',5,149.00,'https://picsum.photos/200/300?random=18','卡其色休闲裤，百搭单品',1,85,32,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'裤子'),(119,'工装裤',5,199.00,'https://picsum.photos/200/300?random=19','多口袋工装裤，帅气机能',1,65,22,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'裤子'),(120,'束脚裤',5,139.00,'https://picsum.photos/200/300?random=20','针织束脚裤，运动休闲',1,95,40,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'裤子'),(121,'休闲运动鞋',6,299.00,'https://picsum.photos/200/300?random=21','轻便休闲鞋，日常出街',1,75,28,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'鞋子'),(122,'板鞋',6,259.00,'https://picsum.photos/200/300?random=22','经典板鞋，耐磨防滑',1,80,30,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'鞋子'),(123,'训练鞋',6,349.00,'https://picsum.photos/200/300?random=23','多功能训练鞋，稳定支撑',1,55,18,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'鞋子'),(124,'乐福鞋',7,369.00,'https://picsum.photos/200/300?random=24','一脚蹬乐福鞋，舒适简约',1,45,15,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'鞋子'),(125,'牛津鞋',7,429.00,'https://picsum.photos/200/300?random=25','英伦牛津鞋，雕花设计',1,40,12,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'鞋子'),(126,'切尔西靴',7,549.00,'https://picsum.photos/200/300?random=26','侧边松紧切尔西靴，时尚有型',1,35,10,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'鞋子'),(127,'贝雷帽',8,69.00,'https://picsum.photos/200/300?random=27','法式贝雷帽，优雅文艺',1,130,70,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(128,'太阳帽',8,89.00,'https://picsum.photos/200/300?random=28','宽檐太阳帽，防晒必备',1,110,55,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(129,'鸭舌帽',8,59.00,'https://picsum.photos/200/300?random=29','弯檐鸭舌帽，街头潮流',1,140,75,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(130,'双肩包',9,249.00,'https://picsum.photos/200/300?random=30','轻便双肩包，大容量',1,70,28,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(131,'托特包',9,299.00,'https://picsum.photos/200/300?random=31','帆布托特包，简约实用',1,60,22,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(132,'单肩包',9,189.00,'https://picsum.photos/200/300?random=32','斜挎单肩包，小巧精致',1,85,35,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(133,'机械手表',10,1299.00,'https://picsum.photos/200/300?random=33','全自动机械表，背透设计',1,25,8,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(134,'电子手表',10,399.00,'https://picsum.photos/200/300?random=34','运动电子表，计时准确',1,50,18,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(135,'石英表',10,699.00,'https://picsum.photos/200/300?random=35','简约石英表，轻薄时尚',1,40,12,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(136,'运动发带',8,29.00,'https://picsum.photos/200/300?random=36','吸汗运动发带，瑜伽跑步',1,200,90,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(137,'腰带',5,79.00,'https://picsum.photos/200/300?random=37','真皮腰带，商务休闲',1,150,65,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(138,'围巾',1,99.00,'https://picsum.photos/200/300?random=38','羊绒围巾，秋冬保暖',1,120,50,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(139,'袜子',6,19.00,'https://picsum.photos/200/300?random=39','棉质短袜，多色可选',1,300,120,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰'),(140,'太阳镜',8,159.00,'https://picsum.photos/200/300?random=40','偏光太阳镜，防紫外线',1,90,35,'2026-04-15 21:45:24','2026-04-15 21:45:24',1,1,'配饰');
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_sku`
--

DROP TABLE IF EXISTS `product_sku`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `product_sku`
--

LOCK TABLES `product_sku` WRITE;
/*!40000 ALTER TABLE `product_sku` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_sku` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `seckill_activity`
--

DROP TABLE IF EXISTS `seckill_activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `seckill_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '活动名称',
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
-- Dumping data for table `seckill_activity`
--

LOCK TABLES `seckill_activity` WRITE;
/*!40000 ALTER TABLE `seckill_activity` DISABLE KEYS */;
INSERT INTO `seckill_activity` VALUES (2,'夏季促销活动','2026-04-17 12:43:45','2026-04-19 12:43:45',0,'2026-04-13 12:43:45','2026-04-13 12:43:45',1,1),(3,'秋季新品活动','2026-04-21 12:43:45','2026-04-24 12:43:45',0,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1);
/*!40000 ALTER TABLE `seckill_activity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `seckill_coupon`
--

DROP TABLE IF EXISTS `seckill_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `seckill_coupon`
--

LOCK TABLES `seckill_coupon` WRITE;
/*!40000 ALTER TABLE `seckill_coupon` DISABLE KEYS */;
INSERT INTO `seckill_coupon` VALUES (1,'100元优惠券',100.00,50.00,100,1,1,'2026-05-01 10:38:47','2026-05-02 17:42:44','2026-05-03 00:00:00','2026-05-30 00:00:00'),(2,'200元优惠券',200.00,100.00,50,1,1,'2026-04-12 12:43:45','2026-04-19 17:42:37','2026-04-15 00:00:00','2026-04-19 23:00:00'),(3,'300元优惠券',300.00,150.00,29,1,1,'2026-04-12 12:43:45','2026-04-19 17:42:24','2026-04-18 00:00:00','2026-04-22 00:00:00'),(4,'夏季折扣券',50.00,25.00,200,2,1,'2026-04-13 12:43:45','2026-04-19 16:30:19','2026-04-09 05:08:01','2026-04-20 02:07:06'),(5,'春季新品券',150.00,75.00,150,1,1,'2026-04-14 12:43:45','2026-04-19 16:29:57','2026-04-15 00:00:00','2026-04-23 00:00:00');
/*!40000 ALTER TABLE `seckill_coupon` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `seckill_order`
--

DROP TABLE IF EXISTS `seckill_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  UNIQUE KEY `uk_seckill_order_active_user_coupon` (`user_id`,`coupon_id`,`active_marker`),
  UNIQUE KEY `idx_seckill_order_number` (`order_number`),
  KEY `idx_seckill_order_user` (`user_id`,`create_time` DESC),
  KEY `idx_sorder_status` (`status`),
  CONSTRAINT `chk_seckill_order_status_b5` CHECK ((`status` in (1,2,3)))
) ENGINE=InnoDB AUTO_INCREMENT=44475 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='秒杀订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `seckill_order`
--

LOCK TABLES `seckill_order` WRITE;
/*!40000 ALTER TABLE `seckill_order` DISABLE KEYS */;
INSERT INTO `seckill_order` (`id`,`user_id`,`coupon_id`,`order_number`,`status`,`create_time`,`pay_time`) VALUES (44474,20,3,'583120253714694148',2,'2026-04-21 17:24:25','2026-04-21 18:18:37');
/*!40000 ALTER TABLE `seckill_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shopping_cart`
--

DROP TABLE IF EXISTS `shopping_cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `shopping_cart`
--

LOCK TABLES `shopping_cart` WRITE;
/*!40000 ALTER TABLE `shopping_cart` DISABLE KEYS */;
INSERT INTO `shopping_cart` VALUES (113,'太阳镜','https://picsum.photos/200/300?random=40',20,140,NULL,'M',1,159.00,'2026-04-22 13:02:54');
/*!40000 ALTER TABLE `shopping_cart` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `special_offer`
--

DROP TABLE IF EXISTS `special_offer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `special_offer`
--

LOCK TABLES `special_offer` WRITE;
/*!40000 ALTER TABLE `special_offer` DISABLE KEYS */;
INSERT INTO `special_offer` VALUES (1,1,99.00,79.00,50,'2026-04-13 12:43:45','2026-04-16 12:43:45',1,'2026-04-12 12:43:45','2026-04-12 12:43:45',1,1),(2,2,199.00,159.00,30,'2026-04-13 12:43:45','2026-04-16 12:43:45',1,'2026-04-12 12:43:45','2026-04-12 12:43:45',1,1),(3,6,299.00,249.00,40,'2026-04-13 12:43:45','2026-04-16 12:43:45',1,'2026-04-12 12:43:45','2026-04-12 12:43:45',1,1),(4,9,299.00,239.00,20,'2026-04-17 12:43:45','2026-04-19 12:43:45',0,'2026-04-13 12:43:45','2026-04-13 12:43:45',1,1),(5,13,599.00,499.00,15,'2026-04-21 12:43:45','2026-04-24 12:43:45',1,'2026-04-14 12:43:45','2026-04-14 18:54:28',1,1),(6,2,123.00,12.00,11,'2026-04-05 16:00:00','2026-04-22 16:00:00',0,'2026-04-14 18:54:52','2026-04-14 18:54:52',NULL,NULL);
/*!40000 ALTER TABLE `special_offer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (10,NULL,'李三','13800138001','男','110101199001011234','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20male&image_size=square','2026-04-14 12:43:45','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(11,NULL,'李四','13800138002','女','110101199001011235','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20female&image_size=square','2026-04-14 12:43:45','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(12,NULL,'王五','13800138003','男','110101199001011236','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20male&image_size=square','2026-04-14 12:43:45','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(13,NULL,'赵六','13800138004','女','110101199001011237','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20female&image_size=square','2026-04-14 12:43:45','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(14,NULL,'钱七','13800138005','男','110101199001011238','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20male&image_size=square','2026-04-14 12:43:45','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(15,NULL,'大王来了','18956235623','男',NULL,'https://picsum.photos/200/300','2026-04-15 12:27:37','$2a$10$tJhij.ddxSukBu2.Y4MkMOBEcnlylT5WwbOzBt5h.do1OTk13n0Vi'),(19,NULL,'用户SUIG8','15623452345',NULL,NULL,'https://picsum.photos/200/300','2026-04-15 14:45:49',NULL),(20,NULL,'飒飒法','15623562356','男',NULL,'https://picsum.photos/200/300','2026-04-15 22:03:53','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(21,NULL,'测试用户1','13800000001','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(22,NULL,'测试用户2','13800000002','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(23,NULL,'测试用户3','13800000003','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(24,NULL,'测试用户4','13800000004','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(25,NULL,'测试用户5','13800000005','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(26,NULL,'测试用户6','13800000006','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(27,NULL,'测试用户7','13800000007','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(28,NULL,'测试用户8','13800000008','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(29,NULL,'测试用户9','13800000009','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(30,NULL,'测试用户10','13800000010','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(31,NULL,'测试用户11','13800000011','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(32,NULL,'测试用户12','13800000012','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(33,NULL,'测试用户13','13800000013','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(34,NULL,'测试用户14','13800000014','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(35,NULL,'测试用户15','13800000015','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(36,NULL,'测试用户16','13800000016','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(37,NULL,'测试用户17','13800000017','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(38,NULL,'测试用户18','13800000018','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(39,NULL,'测试用户19','13800000019','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(40,NULL,'测试用户20','13800000020','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(41,NULL,'测试用户21','13800000021','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(42,NULL,'测试用户22','13800000022','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(43,NULL,'测试用户23','13800000023','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(44,NULL,'测试用户24','13800000024','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(45,NULL,'测试用户25','13800000025','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(46,NULL,'测试用户26','13800000026','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(47,NULL,'测试用户27','13800000027','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(48,NULL,'测试用户28','13800000028','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(49,NULL,'测试用户29','13800000029','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(50,NULL,'测试用户30','13800000030','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(51,NULL,'测试用户31','13800000031','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(52,NULL,'测试用户32','13800000032','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(53,NULL,'测试用户33','13800000033','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(54,NULL,'测试用户34','13800000034','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(55,NULL,'测试用户35','13800000035','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(56,NULL,'测试用户36','13800000036','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(57,NULL,'测试用户37','13800000037','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(58,NULL,'测试用户38','13800000038','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(59,NULL,'测试用户39','13800000039','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(60,NULL,'测试用户40','13800000040','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(61,NULL,'测试用户41','13800000041','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(62,NULL,'测试用户42','13800000042','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(63,NULL,'测试用户43','13800000043','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(64,NULL,'测试用户44','13800000044','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(65,NULL,'测试用户45','13800000045','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(66,NULL,'测试用户46','13800000046','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(67,NULL,'测试用户47','13800000047','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(68,NULL,'测试用户48','13800000048','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(69,NULL,'测试用户49','13800000049','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(70,NULL,'测试用户50','13800000050','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(71,NULL,'测试用户51','13800000051','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(72,NULL,'测试用户52','13800000052','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(73,NULL,'测试用户53','13800000053','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(74,NULL,'测试用户54','13800000054','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(75,NULL,'测试用户55','13800000055','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(76,NULL,'测试用户56','13800000056','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(77,NULL,'测试用户57','13800000057','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(78,NULL,'测试用户58','13800000058','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(79,NULL,'测试用户59','13800000059','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(80,NULL,'测试用户60','13800000060','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(81,NULL,'测试用户61','13800000061','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(82,NULL,'测试用户62','13800000062','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(83,NULL,'测试用户63','13800000063','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(84,NULL,'测试用户64','13800000064','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(85,NULL,'测试用户65','13800000065','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(86,NULL,'测试用户66','13800000066','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(87,NULL,'测试用户67','13800000067','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(88,NULL,'测试用户68','13800000068','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(89,NULL,'测试用户69','13800000069','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(90,NULL,'测试用户70','13800000070','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(91,NULL,'测试用户71','13800000071','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(92,NULL,'测试用户72','13800000072','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(93,NULL,'测试用户73','13800000073','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(94,NULL,'测试用户74','13800000074','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(95,NULL,'测试用户75','13800000075','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(96,NULL,'测试用户76','13800000076','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(97,NULL,'测试用户77','13800000077','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(98,NULL,'测试用户78','13800000078','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(99,NULL,'测试用户79','13800000079','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(100,NULL,'测试用户80','13800000080','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(101,NULL,'测试用户81','13800000081','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(102,NULL,'测试用户82','13800000082','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(103,NULL,'测试用户83','13800000083','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(104,NULL,'测试用户84','13800000084','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(105,NULL,'测试用户85','13800000085','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(106,NULL,'测试用户86','13800000086','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(107,NULL,'测试用户87','13800000087','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(108,NULL,'测试用户88','13800000088','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(109,NULL,'测试用户89','13800000089','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(110,NULL,'测试用户90','13800000090','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(111,NULL,'测试用户91','13800000091','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(112,NULL,'测试用户92','13800000092','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(113,NULL,'测试用户93','13800000093','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(114,NULL,'测试用户94','13800000094','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(115,NULL,'测试用户95','13800000095','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(116,NULL,'测试用户96','13800000096','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(117,NULL,'测试用户97','13800000097','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(118,NULL,'测试用户98','13800000098','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(119,NULL,'测试用户99','13800000099','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW'),(120,NULL,'测试用户100','13800000100','男',NULL,NULL,'2026-05-05 10:37:50','$2a$10$EvAjgtfAZXBAEvbIGpyvL.Phd/1p1rF1HgFNIVCtoZWGSjRWXVfiW');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
-- B6 durable RabbitMQ reliability state.
DROP TABLE IF EXISTS `seckill_message_log`;
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
  `payload_schema_version` int NOT NULL DEFAULT 1,
  `exchange_name` varchar(128) NOT NULL,
  `routing_key` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `dead_letter_status` varchar(16) NOT NULL DEFAULT 'NONE',
  `confirm_status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `returned` tinyint(1) NOT NULL DEFAULT 0,
  `return_reply_code` int DEFAULT NULL,
  `return_reply_text` varchar(255) DEFAULT NULL,
  `current_correlation_id` varchar(160) DEFAULT NULL,
  `publish_attempt` int NOT NULL DEFAULT 0,
  `consume_attempt` int NOT NULL DEFAULT 0,
  `processing_attempt` int DEFAULT NULL,
  `fallback_attempt` int NOT NULL DEFAULT 0,
  `due_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) DEFAULT NULL,
  `locked_by` varchar(128) DEFAULT NULL,
  `locked_until` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
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
  CONSTRAINT `chk_seckill_message_attempts` CHECK ((`publish_attempt` >= 0) AND (`publish_attempt` <= 5) AND (`consume_attempt` >= 0) AND (`consume_attempt` <= 3) AND (`fallback_attempt` >= 0) AND (`fallback_attempt` <= 3) AND ((`processing_attempt` IS NULL) OR ((`processing_attempt` >= 1) AND (`processing_attempt` <= 3))) AND (((BINARY `status`='PROCESSING') AND (`processing_attempt`=`consume_attempt`)) OR ((BINARY `status`<>'PROCESSING') AND (`processing_attempt` IS NULL))) AND (((BINARY `status`='PROCESSING') AND (`locked_by` IS NOT NULL) AND (`locked_until` IS NOT NULL)) OR ((BINARY `status`<>'PROCESSING') AND (`locked_by` IS NULL) AND (`locked_until` IS NULL))) AND (`payload_schema_version` > 0)),
  CONSTRAINT `chk_seckill_message_domains` CHECK ((BINARY `message_type` IN ('ORDER_CREATE','ORDER_TIMEOUT','BUSINESS_DEAD_LETTER','INVALID_MESSAGE')) AND (BINARY `publish_purpose` IN ('INITIAL','CONSUME_RETRY','TIMEOUT_RECOVERY','TIMEOUT_FALLBACK','DEAD_LETTER')) AND (BINARY `status` IN ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED','RETRY_PUBLISH_PENDING','TIMEOUT_PUBLISH_PENDING','TIMEOUT_FALLBACK_PENDING','DEAD_LETTER_PUBLISH_PENDING','CONSUME_EXHAUSTED','COMPENSATION_PENDING','COMPENSATED','MANUAL_REQUIRED')) AND (BINARY `dead_letter_status` IN ('NONE','PENDING','ACKED','MANUAL_REQUIRED')) AND (BINARY `confirm_status` IN ('PENDING','ACK','NACK','TIMEOUT')) AND (((BINARY `message_type`='ORDER_CREATE') AND (((BINARY `publish_purpose`='INITIAL') AND (BINARY `status` IN ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED','COMPENSATION_PENDING','COMPENSATED','MANUAL_REQUIRED'))) OR ((BINARY `publish_purpose`='CONSUME_RETRY') AND (BINARY `status` IN ('SENT','BROKER_ACKED','PROCESSING','CONSUMED','RETRY_PUBLISH_PENDING','CONSUME_EXHAUSTED','MANUAL_REQUIRED'))))) OR ((BINARY `message_type`='ORDER_TIMEOUT') AND (BINARY `publish_purpose` IN ('TIMEOUT_RECOVERY','TIMEOUT_FALLBACK')) AND (BINARY `status` IN ('PREPARED','SENT','BROKER_ACKED','PROCESSING','CONSUMED','TIMEOUT_PUBLISH_PENDING','TIMEOUT_FALLBACK_PENDING','CONSUME_EXHAUSTED','MANUAL_REQUIRED'))) OR ((BINARY `message_type`='BUSINESS_DEAD_LETTER') AND (BINARY `publish_purpose`='DEAD_LETTER') AND (BINARY `status` IN ('PREPARED','SENT','BROKER_ACKED','DEAD_LETTER_PUBLISH_PENDING','MANUAL_REQUIRED'))) OR ((BINARY `message_type`='INVALID_MESSAGE') AND (BINARY `publish_purpose`='DEAD_LETTER') AND (BINARY `status`='CONSUME_EXHAUSTED'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `seckill_compensation_record`;
CREATE TABLE `seckill_compensation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `compensation_action` varchar(32) NOT NULL,
  `order_number` varchar(50) NOT NULL,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `first_reason` varchar(32) NOT NULL,
  `last_reason` varchar(32) NOT NULL,
  `evidence_mask` bigint NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT 0,
  `next_retry_at` datetime(3) DEFAULT NULL,
  `locked_by` varchar(128) DEFAULT NULL,
  `locked_until` datetime(3) DEFAULT NULL,
  `last_result` varchar(64) DEFAULT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `redis_applied_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_compensation_action_order` (`compensation_action`,`order_number`),
  KEY `idx_seckill_compensation_recovery` (`status`,`next_retry_at`,`id`),
  KEY `idx_seckill_compensation_coupon` (`coupon_id`,`id`),
  CONSTRAINT `chk_seckill_compensation_attempt` CHECK (`attempt_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `seckill_reconciliation_anomaly`;
CREATE TABLE `seckill_reconciliation_anomaly` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anomaly_type` varchar(32) NOT NULL,
  `coupon_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'OPEN',
  `occurrence_count` int NOT NULL DEFAULT 1,
  `clean_scan_count` int NOT NULL DEFAULT 0,
  `sample_user_id` bigint DEFAULT NULL,
  `sample_order_number` varchar(50) DEFAULT NULL,
  `details_hash` char(64) NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `first_seen_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_seen_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `resolved_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_anomaly_type_coupon` (`anomaly_type`,`coupon_id`),
  KEY `idx_seckill_anomaly_status` (`status`,`last_seen_at`,`id`),
  CONSTRAINT `chk_seckill_anomaly_counts` CHECK ((`occurrence_count` > 0) AND (`clean_scan_count` >= 0) AND (`coupon_id` >= 0) AND (((`coupon_id` = 0) AND (BINARY `anomaly_type` = 'INVALID_REGISTRY_MEMBER')) OR ((`coupon_id` > 0) AND (BINARY `anomaly_type` <> 'INVALID_REGISTRY_MEMBER'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- B7 商品评价：每个订单商品最多一条评价。
DROP TABLE IF EXISTS `review`;
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
  KEY `idx_review_product` (`product_id`,`status`,`create_time` DESC),
  KEY `idx_review_user` (`user_id`,`create_time` DESC),
  KEY `idx_review_order` (`order_id`),
  UNIQUE KEY `uk_review_order_product` (`order_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品评价';

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-05 10:40:39
