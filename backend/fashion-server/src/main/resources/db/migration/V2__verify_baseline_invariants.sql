-- V2__verify_baseline_invariants.sql
-- 严格只读：逐对象引用存在性校验（引用即失败，prepare 阶段报错中止迁移）。
-- 清单由收敛库程序化生成，与 V1 同源，防手写漂移。不含任何 DDL/DML。
-- CHECK 约束无法被 SQL 引用，由 workpack pre-baseline diff 校验（见 evidence.md）。

-- table: address_book
SELECT 1 FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `consignee` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `sex` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `phone` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `province_code` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `province_name` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `city_code` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `city_name` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `district_code` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `district_name` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `detail` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `label` FROM `address_book` WHERE 1=0 LIMIT 0;
SELECT `is_default` FROM `address_book` WHERE 1=0 LIMIT 0;

-- table: category
SELECT 1 FROM `category` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `type` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `sort` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `create_user` FROM `category` WHERE 1=0 LIMIT 0;
SELECT `update_user` FROM `category` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `category` USE INDEX (`idx_category_name`) WHERE 1=0 LIMIT 0;

-- table: combination
SELECT 1 FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `category_id` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `price` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `description` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `image` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `stock` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `sales` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `create_user` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT `update_user` FROM `combination` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `combination` USE INDEX (`idx_combination_name`) WHERE 1=0 LIMIT 0;

-- table: combination_product
SELECT 1 FROM `combination_product` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `combination_product` WHERE 1=0 LIMIT 0;
SELECT `combination_id` FROM `combination_product` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `combination_product` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `combination_product` WHERE 1=0 LIMIT 0;
SELECT `price` FROM `combination_product` WHERE 1=0 LIMIT 0;
SELECT `copies` FROM `combination_product` WHERE 1=0 LIMIT 0;

-- table: coupon_template
SELECT 1 FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `type` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `threshold` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `discount` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `total_count` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `per_user_limit` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `valid_type` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `valid_days` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `start_time` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `end_time` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `scope_type` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `apply_category_id` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `apply_product_ids` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `coupon_template` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `coupon_template` WHERE 1=0 LIMIT 0;

-- table: employee
SELECT 1 FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `username` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `password` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `phone` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `sex` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `id_number` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `create_user` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT `update_user` FROM `employee` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `employee` USE INDEX (`idx_username`) WHERE 1=0 LIMIT 0;

-- table: favorite
SELECT 1 FROM `favorite` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `favorite` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `favorite` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `favorite` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `favorite` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `favorite` USE INDEX (`idx_favorite_user_product`) WHERE 1=0 LIMIT 0;

-- table: operation_log
SELECT 1 FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `employee_id` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `employee_name` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `module` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `operation` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `method` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `params` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `ip` FROM `operation_log` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `operation_log` WHERE 1=0 LIMIT 0;

-- table: order_detail
SELECT 1 FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `image` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `order_id` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `combination_id` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `sku_info` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `number` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `amount` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `is_seckill` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `seckill_price` FROM `order_detail` WHERE 1=0 LIMIT 0;
SELECT `original_price` FROM `order_detail` WHERE 1=0 LIMIT 0;

-- table: orders
SELECT 1 FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `number` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `address_book_id` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `order_time` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `checkout_time` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `pay_method` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `pay_status` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `amount` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `remark` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `phone` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `address` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `user_name` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `consignee` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `cancel_reason` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `rejection_reason` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `cancel_time` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `estimated_delivery_time` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `delivery_status` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `delivery_time` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `shipping_fee` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `seckill_activity_id` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `seckill_coupon_id` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `is_seckill` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `seckill_price` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `original_price` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `user_coupon_id` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT `stock_deducted` FROM `orders` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `orders` USE INDEX (`idx_orders_number`) WHERE 1=0 LIMIT 0;

-- table: payment
SELECT 1 FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `order_id` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `order_type` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `pay_no` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `amount` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `pay_method` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `trade_no` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `pay_time` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `active_order_id` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT `active_order_type` FROM `payment` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `payment` USE INDEX (`uk_pay_no`) WHERE 1=0 LIMIT 0;
SELECT 1 FROM `payment` USE INDEX (`uk_payment_active_order`) WHERE 1=0 LIMIT 0;

-- table: product
SELECT 1 FROM `product` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `category_id` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `price` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `image` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `description` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `stock` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `sales` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `create_user` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `update_user` FROM `product` WHERE 1=0 LIMIT 0;
SELECT `tag` FROM `product` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `product` USE INDEX (`idx_product_name`) WHERE 1=0 LIMIT 0;

-- table: product_catalog_revision
SELECT 1 FROM `product_catalog_revision` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `product_catalog_revision` WHERE 1=0 LIMIT 0;
SELECT `item_version` FROM `product_catalog_revision` WHERE 1=0 LIMIT 0;
SELECT `item_state` FROM `product_catalog_revision` WHERE 1=0 LIMIT 0;
SELECT `es_locked_by` FROM `product_catalog_revision` WHERE 1=0 LIMIT 0;
SELECT `es_locked_until` FROM `product_catalog_revision` WHERE 1=0 LIMIT 0;
SELECT `updated_at` FROM `product_catalog_revision` WHERE 1=0 LIMIT 0;

-- table: product_catalog_state
SELECT 1 FROM `product_catalog_state` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `product_catalog_state` WHERE 1=0 LIMIT 0;
SELECT `list_version` FROM `product_catalog_state` WHERE 1=0 LIMIT 0;
SELECT `updated_at` FROM `product_catalog_state` WHERE 1=0 LIMIT 0;

-- table: product_projection_reconcile_run
SELECT 1 FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `mode` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `phase` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `cursor_payload` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `scan_count` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `drift_count` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `repair_count` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `clean_verify_count` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `attempt_count` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `next_retry_at` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `locked_by` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `locked_until` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `last_error_summary` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `started_at` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `completed_at` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `updated_at` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT `active_slot` FROM `product_projection_reconcile_run` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `product_projection_reconcile_run` USE INDEX (`uk_product_reconcile_active`) WHERE 1=0 LIMIT 0;

-- table: product_projection_task
SELECT 1 FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `target` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `catalog_version` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `operation` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `payload` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `payload_sha256` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `attempt_count` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `claim_count` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `repair_count` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `next_retry_at` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `locked_by` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `locked_until` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `last_error_summary` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `created_at` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `updated_at` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `completed_at` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `manual_replay_count` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT `last_replayed_at` FROM `product_projection_task` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `product_projection_task` USE INDEX (`uk_product_projection_fact`) WHERE 1=0 LIMIT 0;

-- table: product_sku
SELECT 1 FROM `product_sku` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `product_sku` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `product_sku` WHERE 1=0 LIMIT 0;
SELECT `sku_name` FROM `product_sku` WHERE 1=0 LIMIT 0;
SELECT `sku_value` FROM `product_sku` WHERE 1=0 LIMIT 0;
SELECT `price` FROM `product_sku` WHERE 1=0 LIMIT 0;
SELECT `stock` FROM `product_sku` WHERE 1=0 LIMIT 0;

-- table: refund
SELECT 1 FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `order_id` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `order_detail_id` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `refund_no` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `reason` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `amount` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `order_status` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `audit_opinion` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `audit_time` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `refund_time` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `refund` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `refund` USE INDEX (`idx_refund_no`) WHERE 1=0 LIMIT 0;
SELECT 1 FROM `refund` USE INDEX (`idx_refund_order`) WHERE 1=0 LIMIT 0;

-- table: review
SELECT 1 FROM `review` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `order_id` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `rating` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `content` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `images` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `review` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `review` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `review` USE INDEX (`uk_review_order_product`) WHERE 1=0 LIMIT 0;

-- table: seckill_activity
SELECT 1 FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `discount` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `start_time` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `end_time` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `create_user` FROM `seckill_activity` WHERE 1=0 LIMIT 0;
SELECT `update_user` FROM `seckill_activity` WHERE 1=0 LIMIT 0;

-- table: seckill_compensation_record
SELECT 1 FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `compensation_action` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `order_number` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `coupon_id` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `first_reason` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `last_reason` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `evidence_mask` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `attempt_count` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `next_retry_at` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `locked_by` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `locked_until` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `last_result` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `last_error` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `version` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `redis_applied_at` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `created_at` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `updated_at` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT `completed_at` FROM `seckill_compensation_record` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `seckill_compensation_record` USE INDEX (`uk_seckill_compensation_action_order`) WHERE 1=0 LIMIT 0;

-- table: seckill_coupon
SELECT 1 FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `original_price` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `seckill_price` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `stock` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `limit_per_user` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `start_time` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;
SELECT `end_time` FROM `seckill_coupon` WHERE 1=0 LIMIT 0;

-- table: seckill_message_log
SELECT 1 FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `message_id` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `message_type` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `publish_purpose` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `business_key` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `source_message_id` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `source_message_id_hash` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `source_message_id_prefix` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `body_sha256` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `body_size` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `coupon_id` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `payload` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `payload_schema_version` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `exchange_name` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `routing_key` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `dead_letter_status` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `confirm_status` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `returned` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `return_reply_code` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `return_reply_text` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `current_correlation_id` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `publish_attempt` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `consume_attempt` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `processing_attempt` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `fallback_attempt` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `due_at` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `next_retry_at` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `locked_by` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `locked_until` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `version` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `last_error` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `created_at` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `updated_at` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `confirmed_at` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT `consumed_at` FROM `seckill_message_log` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `seckill_message_log` USE INDEX (`uk_seckill_message_business`) WHERE 1=0 LIMIT 0;
SELECT 1 FROM `seckill_message_log` USE INDEX (`uk_seckill_message_id`) WHERE 1=0 LIMIT 0;

-- table: seckill_order
SELECT 1 FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `coupon_id` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `order_number` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `pay_time` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT `active_marker` FROM `seckill_order` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `seckill_order` USE INDEX (`idx_seckill_order_number`) WHERE 1=0 LIMIT 0;
SELECT 1 FROM `seckill_order` USE INDEX (`uk_seckill_order_active_user_coupon`) WHERE 1=0 LIMIT 0;

-- table: seckill_reconciliation_anomaly
SELECT 1 FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `anomaly_type` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `coupon_id` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `occurrence_count` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `clean_scan_count` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `sample_user_id` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `sample_order_number` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `details_hash` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `version` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `first_seen_at` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `last_seen_at` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT `resolved_at` FROM `seckill_reconciliation_anomaly` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `seckill_reconciliation_anomaly` USE INDEX (`uk_seckill_anomaly_type_coupon`) WHERE 1=0 LIMIT 0;

-- table: shopping_cart
SELECT 1 FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `image` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `combination_id` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `sku_info` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `number` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `amount` FROM `shopping_cart` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `shopping_cart` WHERE 1=0 LIMIT 0;

-- table: special_offer
SELECT 1 FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `product_id` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `original_price` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `offer_price` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `stock` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `start_time` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `end_time` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `update_time` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `create_user` FROM `special_offer` WHERE 1=0 LIMIT 0;
SELECT `update_user` FROM `special_offer` WHERE 1=0 LIMIT 0;

-- table: user
SELECT 1 FROM `user` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `openid` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `name` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `phone` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `sex` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `id_number` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `avatar` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `create_time` FROM `user` WHERE 1=0 LIMIT 0;
SELECT `password` FROM `user` WHERE 1=0 LIMIT 0;
SELECT 1 FROM `user` USE INDEX (`idx_user_phone`) WHERE 1=0 LIMIT 0;

-- table: user_coupon
SELECT 1 FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `id` FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `user_id` FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `template_id` FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `status` FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `obtain_time` FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `expire_time` FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `use_order_id` FROM `user_coupon` WHERE 1=0 LIMIT 0;
SELECT `use_time` FROM `user_coupon` WHERE 1=0 LIMIT 0;

