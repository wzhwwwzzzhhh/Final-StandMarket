-- Table structure for table `operation_log`
-- 管理端操作日志（审计）：管理员关键写操作入库可追溯
-- 幂等：可重复执行

CREATE TABLE IF NOT EXISTS `operation_log` (
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
