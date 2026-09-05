/*
 Navicat Premium Dump SQL

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 80028 (8.0.28)
 Source Host           : localhost:3306
 Source Schema         : seckills

 Target Server Type    : MySQL
 Target Server Version : 80028 (8.0.28)
 File Encoding         : 65001

 Date: 05/09/2026 22:51:44
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sk_goods
-- ----------------------------
DROP TABLE IF EXISTS `sk_goods`;
CREATE TABLE `sk_goods`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `goods_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `goods_img` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `goods_detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `goods_price` decimal(10, 2) NOT NULL,
  `goods_stock` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sk_goods_seckill
-- ----------------------------
DROP TABLE IF EXISTS `sk_goods_seckill`;
CREATE TABLE `sk_goods_seckill`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `stock_count` int NOT NULL DEFAULT 0 COMMENT '秒杀库存',
  `seckill_price` decimal(10, 2) NOT NULL COMMENT '秒杀价',
  `start_date` datetime NOT NULL COMMENT '秒杀开始时间',
  `end_date` datetime NOT NULL COMMENT '秒杀结束时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_goods_id`(`goods_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sk_mq_outbox
-- ----------------------------
DROP TABLE IF EXISTS `sk_mq_outbox`;
CREATE TABLE `sk_mq_outbox`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exchange` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '交换机',
  `routing_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '路由键',
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息体 JSON',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待发送 1-已发送 2-发送失败',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `next_retry_time` datetime NOT NULL COMMENT '下次重试时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_time`(`status` ASC, `next_retry_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'MQ 消息本地表（可靠发送）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sk_order
-- ----------------------------
DROP TABLE IF EXISTS `sk_order`;
CREATE TABLE `sk_order`  (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `goods_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_goods`(`user_id` ASC, `goods_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sk_order_info
-- ----------------------------
DROP TABLE IF EXISTS `sk_order_info`;
CREATE TABLE `sk_order_info`  (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `goods_id` bigint NOT NULL,
  `delivery_addr_id` bigint NULL DEFAULT NULL,
  `goods_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `goods_count` int NOT NULL DEFAULT 1,
  `goods_price` decimal(10, 2) NOT NULL,
  `order_channel` tinyint NULL DEFAULT 1 COMMENT '1-PC 2-APP',
  `status` tinyint NULL DEFAULT 0 COMMENT '0-新建 1-已支付 2-已发货',
  `create_date` datetime NOT NULL,
  `pay_date` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_goods_id`(`goods_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sk_seckill_order
-- ----------------------------
DROP TABLE IF EXISTS `sk_seckill_order`;
CREATE TABLE `sk_seckill_order`  (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_goods`(`user_id` ASC, `goods_id` ASC) USING BTREE,
  UNIQUE INDEX `idx_user_goods`(`user_id` ASC, `goods_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀订单中间表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sk_user
-- ----------------------------
DROP TABLE IF EXISTS `sk_user`;
CREATE TABLE `sk_user`  (
  `id` bigint NOT NULL COMMENT '手机号/用户ID',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `salt` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `head` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `register_date` datetime NOT NULL,
  `last_login_date` datetime NULL DEFAULT NULL,
  `login_count` int NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
