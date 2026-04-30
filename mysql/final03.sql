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
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='地址簿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `address_book`
--

LOCK TABLES `address_book` WRITE;
/*!40000 ALTER TABLE `address_book` DISABLE KEYS */;
INSERT INTO `address_book` VALUES (4,1,'张三','男','13800138001',NULL,'北京市',NULL,'北京市',NULL,'朝阳区','朝阳区建国路88号','公司',1),(5,1,'张三','男','13800138001',NULL,'北京市',NULL,'北京市',NULL,'海淀区','海淀区中关村大街1号','家',0),(6,2,'李四','女','13800138002',NULL,'上海市',NULL,'上海市',NULL,'浦东新区','浦东新区陆家嘴环路1000号','公司',1),(7,3,'王五','男','13800138003',NULL,'广州市',NULL,'广州市',NULL,'天河区','天河区天河路385号','家',1),(8,4,'赵六','女','13800138004',NULL,'深圳市',NULL,'深圳市',NULL,'南山区','南山区科技园南区','公司',1),(9,5,'钱七','男','13800138005',NULL,'杭州市',NULL,'杭州市',NULL,'西湖区','西湖区文三路999号','家',1);
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
INSERT INTO `combination` VALUES (41,5,'男装休闲套装',599.00,1,'包含T恤和牛仔裤','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=men%20casual%20set&image_size=square',50,89,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(42,5,'男装商务套装',1199.00,1,'包含西装和衬衫','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=men%20business%20set&image_size=square',30,45,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(43,6,'女装休闲套装',499.00,1,'包含卫衣和半身裙','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=women%20casual%20set&image_size=square',60,78,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(44,6,'女装优雅套装',799.00,1,'包含连衣裙和围巾','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=women%20elegant%20set&image_size=square',40,56,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1);
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
INSERT INTO `employee` VALUES (6,'管理员','admin','123456','13800138000','男','110101199001011234',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(7,'张三','zhangsan','123456','13800138001','男','110101199001011235',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(8,'李四','lisi','123456','13800138002','女','110101199001011236',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(9,'王五','wangwu','123456','13800138003','男','110101199001011237',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1),(10,'赵六','zhaoliu','123456','13800138004','女','110101199001011238',1,'2026-04-14 12:43:45','2026-04-14 12:43:45',1,1);
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
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_detail`
--

LOCK TABLES `order_detail` WRITE;
/*!40000 ALTER TABLE `order_detail` DISABLE KEYS */;
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
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (26,'20260414001',5,1,1,'2026-04-11 12:43:45','2026-04-11 13:13:45',1,1,298.00,'尽快送达','13800138001','北京市朝阳区建国路88号','张三','张三',NULL,NULL,NULL,NULL,1,NULL,NULL),(27,'20260414002',3,2,3,'2026-04-12 12:43:45','2026-04-12 13:03:45',2,1,599.00,'','13800138002','上海市浦东新区陆家嘴环路1000号','李四','李四',NULL,NULL,NULL,NULL,1,NULL,NULL),(28,'20260414003',3,3,4,'2026-04-13 12:43:45','2026-04-13 12:58:45',1,1,399.00,'周末送达','13800138003','广州市天河区天河路385号','王五','王五',NULL,NULL,NULL,NULL,1,NULL,NULL),(29,'20260414004',4,4,5,'2026-04-14 12:43:45',NULL,2,0,799.00,'','13800138004','深圳市南山区科技园南区','赵六','赵六',NULL,NULL,NULL,NULL,1,NULL,NULL),(30,'20260414005',4,5,6,'2026-04-09 12:43:45','2026-04-09 13:23:45',1,1,1199.00,'商务活动需要','13800138005','杭州市西湖区文三路999号','钱七','钱七',NULL,NULL,NULL,NULL,1,NULL,NULL);
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
  UNIQUE KEY `idx_product_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product`
--

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` VALUES (1,'纯棉T恤',1,99.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cotton%20t-shirt&image_size=square','舒适纯棉面料，透气性好',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(12,'长袖衬衫',1,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=long%20sleeve%20shirt&image_size=square','商务休闲衬衫，面料舒适',1,80,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(13,'短袖衬衫',1,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=short%20sleeve%20shirt&image_size=square','夏季短袖衬衫，清凉透气',1,90,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(14,'连帽卫衣',1,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=hoodie&image_size=square','时尚连帽卫衣，保暖舒适',1,70,25,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(15,'针织衫',1,169.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=knitwear&image_size=square','柔软针织衫，适合春秋季节',1,85,35,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(16,'牛仔外套',2,299.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=denim%20jacket&image_size=square','经典牛仔外套，百搭时尚',1,50,20,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(17,'休闲外套',2,249.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=casual%20jacket&image_size=square','轻便休闲外套，日常穿搭',1,60,25,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(18,'西装外套',2,499.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=suit%20jacket&image_size=square','商务西装外套，正式场合必备',1,40,15,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(19,'羽绒服',2,599.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=down%20jacket&image_size=square','冬季保暖羽绒服，轻便舒适',1,30,10,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(20,'连衣裙',3,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=dress&image_size=square','优雅连衣裙，适合各种场合',1,80,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(21,'半身裙',3,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=skirt&image_size=square','时尚半身裙，百搭单品',1,90,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(22,'碎花裙',3,249.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=floral%20dress&image_size=square','清新碎花裙，夏季必备',1,70,35,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'衣服'),(23,'牛仔裤',4,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=jeans&image_size=square','修身牛仔裤，舒适耐穿',1,120,60,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(24,'破洞牛仔裤',4,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=ripped%20jeans&image_size=square','时尚破洞牛仔裤，潮流单品',1,80,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(25,'直筒牛仔裤',4,169.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=straight%20jeans&image_size=square','经典直筒牛仔裤，百搭款式',1,90,45,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(26,'休闲裤',5,129.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=casual%20pants&image_size=square','宽松休闲裤，日常百搭',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(27,'运动裤',5,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sports%20pants&image_size=square','透气运动裤，适合健身',1,110,55,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(28,'西裤',5,249.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=trousers&image_size=square','商务西裤，正式场合必备',1,70,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'裤子'),(33,'智能手表',10,899.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smart%20watch&image_size=square','多功能智能手表，科技感十足',1,40,15,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(34,'手表',10,599.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=watch&image_size=square','时尚手表，提升品味',1,30,10,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(35,'斜挎包',9,199.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=crossbody%20bag&image_size=square','时尚斜挎包，方便携带',1,80,40,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(36,'钱包',9,149.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=wallet&image_size=square','精致钱包，收纳有序',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(45,'跑步鞋',6,349.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=running%20shoes&image_size=square','减震跑步鞋，舒适透气',1,70,30,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(46,'休闲皮鞋',7,399.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=casual%20leather%20shoes&image_size=square','时尚休闲皮鞋，日常穿搭',1,50,20,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(47,'靴子',7,599.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=boots&image_size=square','冬季靴子，保暖时尚',1,30,10,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(48,'棒球帽',8,59.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=baseball%20cap&image_size=square','时尚棒球帽，防晒必备',1,150,80,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(49,'渔夫帽',8,79.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=bucket%20hat&image_size=square','潮流渔夫帽，个性十足',1,120,60,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(50,'毛线帽',8,89.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=knit%20hat&image_size=square','冬季毛线帽，保暖舒适',1,100,50,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(51,'手提包',9,299.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=handbag&image_size=square','大容量手提包，实用美观',1,70,35,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'配饰'),(56,'皮鞋',7,499.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=leather%20shoes&image_size=square','商务皮鞋，正式场合必备',1,40,15,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(57,'篮球鞋',6,499.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=basketball%20shoes&image_size=square','专业篮球鞋，支撑性好',1,50,20,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(96,'运动鞋',6,399.00,'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sports%20shoes&image_size=square','轻便运动鞋，适合跑步',1,60,25,'2026-04-14 20:40:36','2026-04-14 20:40:36',1,1,'鞋子'),(99,'超级安全衣',1,500.00,NULL,'超级好看的安全衣',1,20,NULL,NULL,NULL,NULL,NULL,'衣服');
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
  `activity_id` bigint NOT NULL COMMENT '活动id',
  `name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '券名称',
  `original_price` decimal(10,2) NOT NULL COMMENT '原价',
  `seckill_price` decimal(10,2) NOT NULL COMMENT '秒杀价',
  `stock` int NOT NULL COMMENT '库存',
  `limit_per_user` int NOT NULL DEFAULT '1' COMMENT '每人限购',
  `status` int DEFAULT '1' COMMENT '状态 0:无效 1:有效',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='秒杀券';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `seckill_coupon`
--

LOCK TABLES `seckill_coupon` WRITE;
/*!40000 ALTER TABLE `seckill_coupon` DISABLE KEYS */;
INSERT INTO `seckill_coupon` VALUES (1,1,'100元优惠券',100.00,50.00,100,1,1,'2026-04-12 12:43:45','2026-04-12 12:43:45'),(2,1,'200元优惠券',200.00,100.00,50,1,1,'2026-04-12 12:43:45','2026-04-12 12:43:45'),(3,1,'300元优惠券',300.00,150.00,30,1,1,'2026-04-12 12:43:45','2026-04-12 12:43:45'),(4,2,'夏季折扣券',50.00,25.00,200,2,1,'2026-04-13 12:43:45','2026-04-13 12:43:45'),(5,5,'春季新品券',150.00,75.00,150,1,1,'2026-04-14 12:43:45','2026-04-14 18:54:06');
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
  `order_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL COMMENT '订单号',
  `status` int DEFAULT '1' COMMENT '状态 1:待支付 2:已支付 3:已取消',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_coupon` (`user_id`,`coupon_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='秒杀订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `seckill_order`
--

LOCK TABLES `seckill_order` WRITE;
/*!40000 ALTER TABLE `seckill_order` DISABLE KEYS */;
INSERT INTO `seckill_order` VALUES (1,1,1,'SK20260414001',2,'2026-04-13 12:43:45','2026-04-13 12:48:45'),(2,2,2,'SK20260414002',2,'2026-04-13 13:13:45','2026-04-13 13:18:45'),(3,3,3,'SK20260414003',1,'2026-04-14 12:13:45',NULL),(4,4,1,'SK20260414004',2,'2026-04-14 10:43:45','2026-04-14 10:53:45'),(5,5,2,'SK20260414005',3,'2026-04-14 09:43:45',NULL);
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
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='购物车';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shopping_cart`
--

LOCK TABLES `shopping_cart` WRITE;
/*!40000 ALTER TABLE `shopping_cart` DISABLE KEYS */;
INSERT INTO `shopping_cart` VALUES (63,'纯棉T恤','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cotton%20t-shirt&image_size=square',1,1,NULL,'M',1,99.00,'2026-04-14 21:52:43'),(64,'休闲外套','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=casual%20jacket&image_size=square',1,17,NULL,'M',1,249.00,'2026-04-14 21:52:51'),(65,'长袖衬衫','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=long%20sleeve%20shirt&image_size=square',1,12,NULL,'M',1,199.00,'2026-04-14 21:56:07');
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
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='用户信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (10,NULL,'李三','13800138001','男','110101199001011234','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20male&image_size=square','2026-04-14 12:43:45','123456'),(11,NULL,'李四','13800138002','女','110101199001011235','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20female&image_size=square','2026-04-14 12:43:45','123456'),(12,NULL,'王五','13800138003','男','110101199001011236','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20male&image_size=square','2026-04-14 12:43:45','123456'),(13,NULL,'赵六','13800138004','女','110101199001011237','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20female&image_size=square','2026-04-14 12:43:45','123456'),(14,NULL,'钱七','13800138005','男','110101199001011238','https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20male&image_size=square','2026-04-14 12:43:45','123456'),(15,NULL,'大王来了','18956235623','男',NULL,'https://picsum.photos/200/300','2026-04-15 12:27:37','123456987'),(19,NULL,'用户SUIG8','15623452345',NULL,NULL,'https://picsum.photos/200/300','2026-04-15 14:45:49',NULL);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-15 15:22:43
