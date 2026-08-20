/*
 Navicat Premium Data Transfer

 Source Server         : 本地
 Source Server Type    : MySQL
 Source Server Version : 80033
 Source Host           : localhost:3306
 Source Schema         : a_old

 Target Server Type    : MySQL
 Target Server Version : 80033
 File Encoding         : 65001

 Date: 30/04/2026 16:17:59
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- PUBLIC DEMO DATA ONLY: names, addresses, contact numbers and identity values
-- are synthetic fixtures and must never be replaced with real personal data.

-- ----------------------------
-- Table structure for activity
-- ----------------------------
DROP TABLE IF EXISTS `activity`;
CREATE TABLE `activity`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `activityName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动名称',
  `activityTypeId` int NULL DEFAULT NULL COMMENT '活动类型',
  `activityDate` date NULL DEFAULT NULL COMMENT '活动日期',
  `startTime` time NULL DEFAULT NULL COMMENT '开始时间',
  `endTime` time NULL DEFAULT NULL COMMENT '结束时间',
  `activityAddress` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动地点',
  `dId` int NULL DEFAULT NULL COMMENT '负责人id',
  `activityDetail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动内容',
  `image` varchar(600) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图片',
  `state` int NOT NULL DEFAULT 1 COMMENT '活动状态',
  `activityPoint` int NULL DEFAULT NULL COMMENT '活动积分',
  `limitNum` int NOT NULL DEFAULT 0 COMMENT '报名人数限制',
  `signNum` int NOT NULL DEFAULT 0 COMMENT '已报名人数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of activity
-- ----------------------------
INSERT INTO `activity` VALUES (1, '健康监测上门服务', 1, '2026-04-01', '09:00:00', '12:00:00', '各社区家庭', 5, '医护人员上门为老年人测量血压、血糖等基础健康指标', '/image/20240919/1.png', 3, 15, 20, 3);
INSERT INTO `activity` VALUES (2, '康复训练指导', 2, '2026-04-02', '14:00:00', '16:00:00', '各社区家庭', 6, '康复师上门指导老年人进行关节活动和肌肉训练', '/image/20240919/2.png', 3, 20, 15, 2);
INSERT INTO `activity` VALUES (3, '心理疏导陪伴', 3, '2026-04-03', '10:00:00', '12:00:00', '各社区活动室', 7, '心理专家为老年人提供情感支持和心理疏导', '/image/20240919/3.png', 3, 10, 25, 5);
INSERT INTO `activity` VALUES (4, '春季大扫除服务', 4, '2026-04-05', '08:00:00', '17:00:00', '各社区家庭', 18, '志愿者上门帮助老年人进行春季深度清洁', '/image/20240919/4.png', 3, 25, 10, 1);
INSERT INTO `activity` VALUES (5, '爱心代购服务', 5, '2026-04-06', '09:00:00', '11:00:00', '超市及社区家庭', 18, '帮助老年人购买生活必需品并送货上门', '/image/20240919/5.png', 3, 10, 30, 7);
INSERT INTO `activity` VALUES (6, '营养餐制作指导', 6, '2026-04-08', '14:00:00', '16:00:00', '社区食堂', 5, '营养师指导老年人制作适合自身的营养餐', '/image/20240919/6.png', 3, 15, 20, 4);
INSERT INTO `activity` VALUES (7, '书法静心班', 7, '2026-04-10', '09:00:00', '11:00:00', '社区活动中心', 18, '教老年人书法，静心养性，陶冶情操', '/image/20240919/7.png', 3, 12, 15, 3);
INSERT INTO `activity` VALUES (8, '防跌倒安全讲座', 8, '2026-04-12', '14:00:00', '16:00:00', '社区多功能厅', 6, '教授老年人居家防跌倒知识和急救技能', '/image/20240919/8.png', 3, 18, 25, 6);
INSERT INTO `activity` VALUES (9, '园艺疗愈活动', 7, '2026-04-15', '09:00:00', '11:00:00', '社区花园', 18, '通过园艺种植帮助老年人放松心情，感受自然', '/image/20240919/9.png', 3, 15, 20, 2);
INSERT INTO `activity` VALUES (10, '上门理发服务', 1, '2026-04-18', '10:00:00', '16:00:00', '各社区家庭', 18, '专业理发师上门为老年人提供理发服务', '/image/20240919/10.png', 3, 10, 15, 1);
INSERT INTO `activity` VALUES (11, '音乐疗愈沙龙', 3, '2026-04-20', '14:00:00', '16:00:00', '社区音乐室', 7, '通过音乐欣赏和简单乐器演奏缓解压力', '/image/20240919/11.png', 3, 12, 20, 4);
INSERT INTO `activity` VALUES (12, '智能设备使用教学', 8, '2026-04-22', '09:00:00', '12:00:00', '社区电教室', 18, '教老年人使用智能手机、智能手表等设备', '/image/20240919/12.png', 3, 20, 20, 3);
INSERT INTO `activity` VALUES (13, '中医养生讲座', 1, '2026-04-25', '14:00:00', '16:00:00', '社区活动中心', 5, '中医师讲解老年人四季养生方法和穴位按摩', '/image/20240919/14.png', 3, 15, 30, 8);
INSERT INTO `activity` VALUES (14, '手工制作放松课', 7, '2026-04-28', '10:00:00', '12:00:00', '社区手工室', 18, '教老年人制作简单手工艺品，放松身心', '/image/20240919/13.png', 3, 10, 20, 5);
INSERT INTO `activity` VALUES (15, '居家安全评估', 8, '2026-05-01', '09:00:00', '17:00:00', '各社区家庭', 6, '专业人员上门评估老年人居家安全隐患并提供改进建议', '/image/20240919/16.png', 1, 25, 10, 0);

-- Keep one sample activity available whenever the demo database is imported.
UPDATE `activity`
SET `activityDate` = DATE_ADD(CURDATE(), INTERVAL 7 DAY), `state` = 1, `signNum` = 0
WHERE `id` = 15;

-- ----------------------------
-- Table structure for activity_state
-- ----------------------------
DROP TABLE IF EXISTS `activity_state`;
CREATE TABLE `activity_state`  (
  `id` int NOT NULL,
  `state` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of activity_state
-- ----------------------------
INSERT INTO `activity_state` VALUES (0, '已取消');
INSERT INTO `activity_state` VALUES (1, '未开始');
INSERT INTO `activity_state` VALUES (2, '报名截止');
INSERT INTO `activity_state` VALUES (3, '已结束');

-- ----------------------------
-- Table structure for activity_type
-- ----------------------------
DROP TABLE IF EXISTS `activity_type`;
CREATE TABLE `activity_type`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动类型',
  `detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动类型描述',
  `purpose` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '活动目的',
  `state` int NOT NULL DEFAULT 0 COMMENT '活动状态：0：禁用，1：启用',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of activity_type
-- ----------------------------
INSERT INTO `activity_type` VALUES (1, '上门诊疗', '专业医护人员上门提供基础诊疗服务', '为行动不便的老年人提供便捷医疗服务', 1);
INSERT INTO `activity_type` VALUES (2, '康复理疗', '上门进行康复训练和物理治疗', '帮助老年人恢复身体功能，提高生活质量', 1);
INSERT INTO `activity_type` VALUES (3, '心理慰藉', '提供心理疏导和情感陪伴服务', '缓解老年人孤独感，提升心理健康', 1);
INSERT INTO `activity_type` VALUES (4, '居家保洁', '上门提供专业的清洁和整理服务', '为老年人创造干净舒适的生活环境', 1);
INSERT INTO `activity_type` VALUES (5, '代购代办', '帮助老年人购买生活用品和处理日常事务', '减轻老年人外出负担，提供便利', 1);
INSERT INTO `activity_type` VALUES (6, '营养配餐', '为老年人定制并提供营养餐食', '保障老年人营养均衡，促进健康', 1);
INSERT INTO `activity_type` VALUES (7, '文娱活动', '组织适合老年人的文化娱乐活动', '丰富老年人精神文化生活，增进社交', 1);
INSERT INTO `activity_type` VALUES (8, '安全监护', '提供居家安全监测和紧急救助服务', '保障老年人居家安全，及时响应突发状况', 1);

-- ----------------------------
-- Table structure for comment
-- ----------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '留言内容',
  `user_id` int NOT NULL COMMENT '留言用户ID，对应user表的id',
  `user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户姓名，对应user表的name',
  `user_telephone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户电话，对应user表的telephone',
  `parent_id` int NULL DEFAULT NULL COMMENT '父留言ID（用于回复）',
  `reply_to` int NULL DEFAULT NULL COMMENT '回复给哪个用户ID',
  `reply_to_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '被回复用户的姓名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of comment
-- ----------------------------
INSERT INTO `comment` VALUES (1, '欢迎大家使用留言板功能！有关于社区活动、服务预约的任何问题或建议，都可以在这里提出。', 1, 'admin', '11111111111', NULL, NULL, NULL, '2026-04-20 01:27:09', '2026-04-20 01:27:09', 0);
INSERT INTO `comment` VALUES (2, '这个功能太好了，可以方便交流！最近的健康讲座我报名了，有一起去的邻居吗？', 8, '康凯', '10000000008', NULL, NULL, NULL, '2026-04-20 10:27:09', '2026-04-20 22:04:45', 0);
INSERT INTO `comment` VALUES (3, '是的，非常方便！康叔，我也报名了健康讲座，到时候咱们坐一块儿。', 9, '赵晓', '10000000009', 2, 8, '康凯', '2026-04-20 11:27:09', '2026-04-20 22:04:45', 0);
INSERT INTO `comment` VALUES (4, '请问下周的书法培训班什么时候开始报名？需要自备毛笔吗？', 10, '章杉', '10000000010', NULL, NULL, NULL, '2026-04-20 14:27:09', '2026-04-20 01:27:09', 0);
INSERT INTO `comment` VALUES (5, '管理员您好，我上周申请的购物代购服务完成得很好，感谢工作人员小杨！', 12, '提某', '10000000012', NULL, NULL, NULL, '2026-04-20 15:28:45', '2026-04-21 00:51:07', 0);
INSERT INTO `comment` VALUES (6, '社区电影放映能多放一些戏曲类的片子吗？我们几个老票友都很期待。', 11, '缇娜', '10000000011', NULL, NULL, NULL, '2026-04-20 16:33:58', '2026-04-21 00:51:03', 0);
INSERT INTO `comment` VALUES (7, '没问题赵姐，到时候见！', 8, '康凯', '10000000008', 3, 9, '赵晓', '2026-04-20 16:16:53', '2026-04-20 22:04:45', 0);
INSERT INTO `comment` VALUES (8, '你好，欢迎使用留言板。', 1, 'admin', '11111111111', 1, 1, 'admin', '2026-04-20 16:17:40', '2026-04-20 02:17:40', 0);
INSERT INTO `comment` VALUES (9, '@章杉 书法班是每周三下午，工具社区会提供基础的，如果您有自己用惯的也可以带来。具体报名看活动页面。', 2, '欧文', '12111111111', 4, 10, '章杉', '2026-04-20 17:31:26', '2026-04-20 22:40:03', 0);
INSERT INTO `comment` VALUES (10, '谢谢欧文回复，明白了。', 10, '章杉', '10000000010', 9, 2, '欧文', '2026-04-20 18:31:32', '2026-04-20 22:04:45', 0);
INSERT INTO `comment` VALUES (11, '最近感觉腰不太舒服，社区有推拿或者理疗相关的服务可以预约吗？', 9, '赵晓', '10000000009', NULL, NULL, NULL, '2026-04-20 19:27:27', '2026-04-20 22:39:34', 0);
INSERT INTO `comment` VALUES (12, '@赵晓 阿姨您好，目前我们有定期的健康咨询活动，医护人员可以给您一些建议。专业的推拿理疗服务我们正在筹划中，请关注后续通知。', 5, '流星', '10000000001', 11, 9, '赵晓', '2026-04-20 20:27:32', '2026-04-20 22:39:34', 0);
INSERT INTO `comment` VALUES (13, '社区活动越来越丰富了，为你们点赞！', 17, '黄黄黄', '10000000015', NULL, NULL, NULL, '2026-04-20 20:41:55', '2026-04-20 22:41:55', 0);
INSERT INTO `comment` VALUES (14, '希望多组织一些户外的散步或者公园游玩活动，春天了想出去走走。', 8, '康凯', '10000000008', NULL, NULL, NULL, '2026-04-21 10:44:55', '2026-04-23 03:45:53', 1);
INSERT INTO `comment` VALUES (15, '支持康叔的建议！天气好，户外活动对身心都好。', 10, '章杉', '10000000010', 14, 8, '康凯', '2026-04-21 11:30:15', '2026-04-23 03:45:53', 1);
INSERT INTO `comment` VALUES (16, '已收到大家的建议，户外游览活动已在规划中，预计下个月推出，请留意公告。', 1, 'admin', '11111111111', 14, 8, '康凯', '2026-04-21 14:20:10', '2026-04-23 03:45:53', 1);
INSERT INTO `comment` VALUES (17, '累死', 17, '林格', '10000000015', NULL, NULL, NULL, '2026-04-23 20:21:42', '2026-04-24 00:37:56', 1);
INSERT INTO `comment` VALUES (18, '大饥荒', 17, '林格', '10000000015', NULL, NULL, NULL, '2026-04-27 03:54:34', '2026-04-27 03:54:40', 1);

-- ----------------------------
-- Table structure for emergency_help
-- ----------------------------
DROP TABLE IF EXISTS `emergency_help`;
CREATE TABLE `emergency_help`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `u_id` int NOT NULL COMMENT '用户ID',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名（冗余）',
  `telephone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户电话',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户地址',
  `reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '求助原因',
  `status` int NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-处理中，2-已处理，3-已取消',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `admin_id` int NULL DEFAULT NULL COMMENT '处理的管理员ID',
  `handle_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `handle_result` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '处理结果描述',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_user`(`u_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '紧急求助表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of emergency_help
-- ----------------------------
INSERT INTO `emergency_help` VALUES (1, 17, 'linge', '10000000015', '啛啛喳喳', '突发头晕，需要紧急帮助', 2, '2026-03-25 10:00:00', 1, '2026-03-25 10:30:00', '已联系120送往医院检查');
INSERT INTO `emergency_help` VALUES (2, 20, 'user001', '10000000006', '西安市新城区西五路1号', '摔倒无法起身', 2, '2026-03-25 11:00:00', 1, '2026-03-26 15:16:15', '已经完成处理完毕');
INSERT INTO `emergency_help` VALUES (3, 21, 'user002', '10000000007', '西安市碑林区南大街2号', '突然胸闷气短', 3, '2026-03-25 12:00:00', 1, '2026-03-26 15:16:39', '管理员取消：误报');
INSERT INTO `emergency_help` VALUES (4, 17, 'linge', '10000000015', '啛啛喳喳', '测试', 3, '2026-03-26 15:54:20', NULL, '2026-03-26 15:55:00', '用户自行取消');
INSERT INTO `emergency_help` VALUES (5, 17, 'linge', '10000000015', '啛啛喳喳', '身体不舒服', 2, '2026-03-26 15:55:12', 1, '2026-03-26 16:07:45', '轻轻松松直接完成');
INSERT INTO `emergency_help` VALUES (6, 17, 'linge', '10000000015', '啛啛喳喳', '身体不要束缚', 3, '2026-03-26 16:08:10', 1, '2026-03-26 16:08:32', '管理员取消：按错了');
INSERT INTO `emergency_help` VALUES (7, 34, 'lingling', '10000000018', NULL, '不舒服', 1, '2026-03-27 18:15:30', 1, '2026-03-27 18:15:37', '管理员已接单，正在处理中');
INSERT INTO `emergency_help` VALUES (8, 34, 'lingling', '10000000018', '冲冲', '不舒服', 2, '2026-03-28 00:04:21', 1, '2026-03-28 00:04:50', '服务完毕..');

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '消息标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '消息内容',
  `type` int NOT NULL DEFAULT 1 COMMENT '消息类型：1-活动通知，2-服务通知，3-资讯通知，4-求助处理通知，5-系统通知',
  `sender_id` int NULL DEFAULT NULL COMMENT '发送者ID',
  `sender_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发送者名称',
  `receiver_id` int NOT NULL DEFAULT 0 COMMENT '接收者ID（0表示发给所有用户）',
  `related_id` int NULL DEFAULT NULL COMMENT '关联ID（如活动ID、求助ID等）',
  `related_type` int NULL DEFAULT NULL COMMENT '关联类型：1-活动，2-服务，3-资讯，4-求助',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_receiver`(`receiver_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_sender`(`sender_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息通知表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of message
-- ----------------------------
INSERT INTO `message` VALUES (4, '测试', '测测测测测测测', 5, 1, 'admin', 0, NULL, NULL, '2026-03-26 16:04:49');
INSERT INTO `message` VALUES (5, '从深层次村上春树', '1111111111111111111111', 5, 1, 'admin', 0, NULL, NULL, '2026-03-26 16:05:50');
INSERT INTO `message` VALUES (6, '你在干嘛', '您需要去一趟医院哈', 5, 1, 'admin', 0, NULL, NULL, '2026-03-27 10:16:27');
INSERT INTO `message` VALUES (7, '新活动发布：测测测测测测', '社区发布了新的活动：测测测测测测，快来报名参加吧！', 1, 27, '王建军', 0, 16, 1, '2026-03-27 14:49:12');
INSERT INTO `message` VALUES (8, '新资讯发布：冲冲冲', '社区发布了新的资讯：【冲冲冲】，快来查看吧！', 3, 1, 'admin', 0, 10, 3, '2026-03-27 14:56:27');
INSERT INTO `message` VALUES (9, '服务类型新增', '服务类型【查收】已新增', 2, 1, 'admin', 0, 28, 2, '2026-03-27 15:10:56');
INSERT INTO `message` VALUES (10, '服务类型新增', '服务类型【惆怅长岑长】已新增', 2, 1, 'admin', 0, 29, 2, '2026-03-27 15:12:25');
INSERT INTO `message` VALUES (11, '服务类型更新', '服务类型【上门基础诊疗】已更新', 2, 1, 'admin', 0, 6, 2, '2026-03-27 15:27:31');
INSERT INTO `message` VALUES (12, '服务类型更新', '服务类型【上门基础诊疗】已更新', 2, 1, 'admin', 0, 6, 2, '2026-03-27 15:27:53');
INSERT INTO `message` VALUES (13, '服务类型更新', '服务类型【医疗健康服务】已更新', 2, 1, 'admin', 0, 1, 2, '2026-03-27 15:28:10');
INSERT INTO `message` VALUES (14, '服务类型更新', '服务类型【医疗健康服务】已更新', 2, 1, 'admin', 0, 1, 2, '2026-03-27 15:28:18');
INSERT INTO `message` VALUES (15, '服务类型更新', '服务类型【医疗健康服务】已更新', 2, 1, 'admin', 0, 1, 2, '2026-03-27 15:29:16');
INSERT INTO `message` VALUES (16, '服务类型更新', '服务类型【医疗健康服务】已更新', 2, 1, 'admin', 0, 1, 2, '2026-03-27 15:36:14');
INSERT INTO `message` VALUES (17, '服务类型更新', '服务类型【医疗健康服务】已更新', 2, 1, 'admin', 0, 1, 2, '2026-03-27 15:36:19');
INSERT INTO `message` VALUES (18, '测试111111111', '测试111111111', 5, 1, 'admin', 0, NULL, NULL, '2026-03-27 16:05:34');
INSERT INTO `message` VALUES (19, '测试222222222222', '测试222222222222', 5, 1, 'admin', 0, NULL, NULL, '2026-03-27 16:05:50');

-- ----------------------------
-- Table structure for message_receiver
-- ----------------------------
DROP TABLE IF EXISTS `message_receiver`;
CREATE TABLE `message_receiver`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `message_id` int NOT NULL COMMENT '消息ID',
  `receiver_id` int NOT NULL COMMENT '接收者ID',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
  `read_time` datetime NULL DEFAULT NULL COMMENT '阅读时间',
  `status` int NULL DEFAULT 1 COMMENT '状态：1-正常，0-删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_receiver`(`message_id` ASC, `receiver_id` ASC) USING BTREE,
  INDEX `idx_receiver_status`(`receiver_id` ASC, `is_read` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 134 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息接收表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of message_receiver
-- ----------------------------
INSERT INTO `message_receiver` VALUES (1, 4, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (2, 4, 17, 1, '2026-03-26 16:32:31', 1);
INSERT INTO `message_receiver` VALUES (3, 5, 17, 1, '2026-03-26 16:32:30', 1);
INSERT INTO `message_receiver` VALUES (4, 5, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (5, 5, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (6, 5, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (7, 5, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (8, 5, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (9, 5, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (10, 5, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (11, 5, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (12, 6, 34, 1, '2026-03-27 10:16:47', 1);
INSERT INTO `message_receiver` VALUES (13, 7, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (14, 7, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (15, 7, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (16, 7, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (17, 7, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (18, 7, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (19, 7, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (20, 7, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (21, 7, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (22, 7, 34, 1, '2026-03-27 14:50:13', 1);
INSERT INTO `message_receiver` VALUES (23, 8, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (24, 8, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (25, 8, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (26, 8, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (27, 8, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (28, 8, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (29, 8, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (30, 8, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (31, 8, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (32, 8, 34, 1, '2026-03-27 14:56:37', 1);
INSERT INTO `message_receiver` VALUES (33, 9, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (34, 9, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (35, 9, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (36, 9, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (37, 9, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (38, 9, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (39, 9, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (40, 9, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (41, 9, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (42, 9, 34, 1, '2026-03-27 15:12:52', 1);
INSERT INTO `message_receiver` VALUES (43, 10, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (44, 10, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (45, 10, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (46, 10, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (47, 10, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (48, 10, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (49, 10, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (50, 10, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (51, 10, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (52, 10, 34, 1, '2026-03-27 15:12:50', 1);
INSERT INTO `message_receiver` VALUES (53, 11, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (54, 11, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (55, 11, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (56, 11, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (57, 11, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (58, 11, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (59, 11, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (60, 11, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (61, 11, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (62, 11, 34, 1, '2026-03-27 15:42:03', 1);
INSERT INTO `message_receiver` VALUES (63, 12, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (64, 12, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (65, 12, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (66, 12, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (67, 12, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (68, 12, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (69, 12, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (70, 12, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (71, 12, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (72, 12, 34, 1, '2026-03-27 15:42:03', 1);
INSERT INTO `message_receiver` VALUES (73, 13, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (74, 13, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (75, 13, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (76, 13, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (77, 13, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (78, 13, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (79, 13, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (80, 13, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (81, 13, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (82, 13, 34, 1, '2026-03-27 15:42:03', 1);
INSERT INTO `message_receiver` VALUES (83, 14, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (84, 14, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (85, 14, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (86, 14, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (87, 14, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (88, 14, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (89, 14, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (90, 14, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (91, 14, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (92, 14, 34, 1, '2026-03-27 15:28:37', 1);
INSERT INTO `message_receiver` VALUES (93, 15, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (94, 15, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (95, 15, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (96, 15, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (97, 15, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (98, 15, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (99, 15, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (100, 15, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (101, 15, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (102, 15, 34, 1, '2026-03-27 15:42:03', 1);
INSERT INTO `message_receiver` VALUES (103, 16, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (104, 16, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (105, 16, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (106, 16, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (107, 16, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (108, 16, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (109, 16, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (110, 16, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (111, 16, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (112, 16, 34, 1, '2026-03-27 15:42:03', 1);
INSERT INTO `message_receiver` VALUES (113, 17, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (114, 17, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (115, 17, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (116, 17, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (117, 17, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (118, 17, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (119, 17, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (120, 17, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (121, 17, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (122, 17, 34, 1, '2026-03-27 15:42:03', 1);
INSERT INTO `message_receiver` VALUES (123, 18, 17, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (124, 18, 19, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (125, 18, 20, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (126, 18, 21, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (127, 18, 22, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (128, 18, 23, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (129, 18, 24, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (130, 18, 25, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (131, 18, 26, 0, NULL, 1);
INSERT INTO `message_receiver` VALUES (132, 18, 34, 1, '2026-03-27 16:06:13', 1);
INSERT INTO `message_receiver` VALUES (133, 19, 34, 1, '2026-03-27 16:06:13', 1);

-- ----------------------------
-- Table structure for news
-- ----------------------------
DROP TABLE IF EXISTS `news`;
CREATE TABLE `news`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `createTime` date NULL DEFAULT NULL,
  `view` int NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of news
-- ----------------------------
INSERT INTO `news` VALUES (5, '智慧养老社区成新趋势，科技助力老年人生活', '/image/20250430/f6ee35681c9c4045b6dcccb52afa3998.png', '<ul><li>内容​​：多地推出\"5G+智慧养老\"试点，通过智能手环、居家监测设备等实时追踪老人健康数据，北京某社区已实现跌倒自动报警、用药提醒等功能。</li><li>​​亮点​​：上海某养老院引入VR技术，让老人\"虚拟旅行\"缓解孤独。</li><li>​​来源​​：2023年《中国老龄事业发展报告》</li></ul><p><br></p>', '2026-03-21', 9);
INSERT INTO `news` VALUES (6, '​​\"时间银行\"互助养老模式兴起​', '/image/20250430/2757166172d84ec38563fc710c413c06.png', '<ul><li>​内容​​：南京、成都等地推行\"年轻存时间，老时换服务\"机制，志愿者为老人提供服务可累积积分，未来兑换自身养老服务。</li><li>​​案例​​：杭州某社区1年内超500人参与，累计服务时长超1万小时。</li><li>​​政策支持​​：民政部将纳入全国居家养老服务试点。</li></ul><p><br></p>', '2026-03-24', 5);
INSERT INTO `news` VALUES (7, '​​最新政策：国务院印发《养老服务体系规划》​', '/image/20250430/86372a160ba2407d82363ed98066aeb4.png', '<ul><li>​​要点​​：</li><li class=\"ql-indent-1\">2025年前实现县级养老服务网络全覆盖</li><li class=\"ql-indent-1\">新建小区需配套至少500㎡养老用房</li><li class=\"ql-indent-1\">护理型床位补贴提高至每床3万元</li><li>​​影响​​：预计带动社会资本投入超千亿元。</li></ul><h3><br></h3><p><br></p>', '2026-03-22', 9);

-- ----------------------------
-- Table structure for order_state
-- ----------------------------
DROP TABLE IF EXISTS `order_state`;
CREATE TABLE `order_state`  (
  `id` int NOT NULL,
  `state` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单状态',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of order_state
-- ----------------------------
INSERT INTO `order_state` VALUES (0, '待受理');
INSERT INTO `order_state` VALUES (1, '已取消');
INSERT INTO `order_state` VALUES (2, '待完成');
INSERT INTO `order_state` VALUES (3, '待评价');
INSERT INTO `order_state` VALUES (4, '已结束');

-- ----------------------------
-- Table structure for recipe
-- ----------------------------
DROP TABLE IF EXISTS `recipe`;
CREATE TABLE `recipe`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '菜谱名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '菜谱描述',
  `image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '菜谱图片URL',
  `nutrition_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '营养信息',
  `suitable_crowd` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '适宜人群',
  `cooking_steps` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '烹饪步骤',
  `tips` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '小贴士',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id` int NULL DEFAULT NULL COMMENT '创建人ID',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：1-正常，0-删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_create_user_id`(`create_user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  CONSTRAINT `fk_recipe_create_user` FOREIGN KEY (`create_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of recipe
-- ----------------------------
INSERT INTO `recipe` VALUES (1, '清蒸鲈鱼', '清淡健康，适合老年人食用', NULL, '蛋白质丰富，低脂肪', '高血压、糖尿病患者', '1. 鲈鱼处理干净\n2. 加入姜片、葱段\n3. 蒸10分钟即可', '蒸的时间不宜过长', '2026-04-23 01:33:06', '2026-04-23 01:33:06', 1, 0);
INSERT INTO `recipe` VALUES (2, '南瓜小米粥', '养胃易消化', '/image/20260430/2b1e58f0a1a8493696d58c00f15bb1dc.png', '富含膳食纤维和维生素', '肠胃功能弱者', '1. 南瓜切块\n2. 小米洗净\n3. 煮30分钟', '可加入红枣增加甜味', '2026-04-23 01:33:06', '2026-04-23 01:33:06', 1, 1);
INSERT INTO `recipe` VALUES (3, '青菜豆腐汤', '清淡素食，不错的', '/image/20260430/8001357b86af4903addd5e813ba010b1.png', '低热量，高蛋白', '减肥人群', '1. 豆腐切块\n2. 青菜洗净\n3. 煮5分钟', '不要煮太久，保持青菜鲜绿', '2026-04-23 01:33:06', '2026-04-23 01:33:06', 1, 1);
INSERT INTO `recipe` VALUES (4, '西红柿炒蛋', '家常菜，营养均衡', '/image/20260430/db135a6ca652498f9dc8d9c4429383a5.png', '富含维生素和蛋白质', '全年龄段', '1. 鸡蛋打散炒熟\n2. 西红柿切块炒软\n3. 混合翻炒', '可加入少许糖提鲜', '2026-04-23 01:33:06', '2026-04-23 01:33:06', 1, 1);
INSERT INTO `recipe` VALUES (5, '红烧肉', '经典家常菜', NULL, '高蛋白，适量脂肪', '健康人群', '1. 五花肉焯水\n2. 炒糖色\n3. 炖煮1小时', '用冰糖炒糖色更佳', '2026-04-23 01:33:06', '2026-04-23 01:33:06', 1, 0);
INSERT INTO `recipe` VALUES (6, '你好', '你好电风扇电风扇', '', '', '通用', '', '', '2026-04-23 08:22:38', '2026-04-23 08:22:38', NULL, 0);
INSERT INTO `recipe` VALUES (7, '哈哈哈哈1', '红红火火恍恍惚惚哈哈哈哈', '', '', '通用', '', '', '2026-04-23 19:58:23', '2026-04-23 19:58:23', 1, 0);

-- ----------------------------
-- Table structure for agent_action
-- ----------------------------
DROP TABLE IF EXISTS `agent_action`;
CREATE TABLE `agent_action`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirmation_token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `request_key` char(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `user_id` int NOT NULL,
  `tool_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `arguments_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `requires_confirmation` tinyint NOT NULL DEFAULT 0,
  `status` varchar(20) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `result_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `expires_at` datetime NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `executed_at` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_agent_action_token`(`confirmation_token` ASC) USING BTREE,
  INDEX `idx_agent_action_user_created`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_agent_action_pending`(`user_id` ASC, `request_key` ASC, `status` ASC, `expires_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent操作确认与审计表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for agent_run
-- ----------------------------
DROP TABLE IF EXISTS `agent_run`;
CREATE TABLE `agent_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` char(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `user_id` int NOT NULL,
  `provider` varchar(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `input_modality` varchar(20) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `status` varchar(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `latency_ms` bigint NOT NULL DEFAULT 0,
  `llm_calls` int NOT NULL DEFAULT 0,
  `tool_calls` int NOT NULL DEFAULT 0,
  `successful_tools` int NOT NULL DEFAULT 0,
  `failed_tools` int NOT NULL DEFAULT 0,
  `confirmation_required` tinyint NOT NULL DEFAULT 0,
  `prompt_version` varchar(64) CHARACTER SET ascii COLLATE ascii_general_ci NULL DEFAULT NULL,
  `prompt_tokens` int NOT NULL DEFAULT 0,
  `completion_tokens` int NOT NULL DEFAULT 0,
  `total_tokens` int NOT NULL DEFAULT 0,
  `error_type` varchar(64) CHARACTER SET ascii COLLATE ascii_general_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_agent_run_run_id`(`run_id` ASC) USING BTREE,
  INDEX `idx_agent_run_user_created`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_agent_run_created_status`(`created_at` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent运行与评测指标表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for recipe_order
-- ----------------------------
DROP TABLE IF EXISTS `recipe_order`;
CREATE TABLE `recipe_order`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '用户ID',
  `recipe_id` int NOT NULL COMMENT '菜谱ID',
  `order_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预订时间',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态：0-已预订，1-已完成，2-已取消',
  `complete_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `people_count` int NOT NULL DEFAULT 1 COMMENT '用餐人数',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_recipe_id`(`recipe_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_order_time`(`order_time` ASC) USING BTREE,
  CONSTRAINT `fk_recipe_order_recipe` FOREIGN KEY (`recipe_id`) REFERENCES `recipe` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_recipe_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜谱预订表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of recipe_order
-- ----------------------------
INSERT INTO `recipe_order` VALUES (1, 17, 2, '2026-04-24 02:00:00', 2, NULL, '', 1, '2026-04-23 12:21:10', '2026-04-24 00:29:33');
INSERT INTO `recipe_order` VALUES (2, 17, 6, '2026-04-24 02:00:00', 0, NULL, '', 1, '2026-04-23 12:52:00', '2026-04-24 00:29:33');
INSERT INTO `recipe_order` VALUES (3, 17, 4, '2026-04-24 02:00:00', 0, NULL, '', 1, '2026-04-23 13:12:37', '2026-04-24 00:29:33');
INSERT INTO `recipe_order` VALUES (4, 17, 6, '2026-04-25 02:00:00', 1, '2026-04-24 00:34:22', '哈哈哈哈', 1, '2026-04-23 16:09:22', '2026-04-24 00:34:22');
INSERT INTO `recipe_order` VALUES (5, 17, 6, '2026-04-25 02:00:00', 0, NULL, '额外风温柔染头发v', 1, '2026-04-24 08:31:32', '2026-04-24 16:31:31');

-- ----------------------------
-- Table structure for report
-- ----------------------------
DROP TABLE IF EXISTS `report`;
CREATE TABLE `report`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `uId` int NULL DEFAULT NULL COMMENT '用户编号',
  `dId` int NULL DEFAULT NULL COMMENT '医护人员编号',
  `time` datetime NULL DEFAULT NULL COMMENT '体检日期',
  `height` double NULL DEFAULT NULL COMMENT '身高',
  `weight` double NULL DEFAULT NULL COMMENT '体重',
  `bp` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '血压',
  `WBC` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '白细胞',
  `RBC` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '红细胞',
  `PLT` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '血小板',
  `HGB` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '血红蛋白',
  `LYM` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '淋巴细胞',
  `NEUT` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '中性粒细胞比率',
  `TCHO` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '总胆固醇',
  `TG` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '甘油三酯',
  `HDLC` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '高密度脂蛋白胆固醇',
  `LDLC` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '低密度脂蛋白胆固醇',
  `ALT` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '谷丙转氨酶',
  `AST` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '谷草转氨酶',
  `SB` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '血清胆红素',
  `BS` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '血糖',
  `GLU` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '尿葡萄糖',
  `BIL` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '尿胆红素',
  `KET` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '尿酮体',
  `URO` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '尿胆原',
  `PH` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '尿酸碱度',
  `SG` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '尿比重',
  `UA` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '血尿酸',
  `Scr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '血肌酐',
  `BUN` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '尿素氮',
  `AFP` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '甲胎蛋白',
  `CEA` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '癌胚抗原',
  `ferritin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '铁蛋白',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of report
-- ----------------------------
INSERT INTO `report` VALUES (1, 8, 1, '2023-11-17 12:55:27', 177, 61, '130/85', '5.9', '5.83', '170', '167', '40.4', '44.5', '3.52', '1.36', '1.62', '2.93', '32.1', '31', '17.4', '5.8', '4.92', '阴性', '阴性', '弱阳性', '6.5', '1.015', '301.6', '75.4', '2.84', '3.1', '2.5', '156.7');
INSERT INTO `report` VALUES (2, 10, 1, '2024-05-20 12:57:25', 163, 52, '118/76', '4.3', '4.15', '210', '125', '50', '50', '5.2', '1.05', '1.8', '2.9', '20', '22', '12.1', '5.3', '阴性', '阴性', '阴性', '阴性', '6.0', '1.020', '280.5', '68.2', '5.84', '2.8', '1.9', '89.3');
INSERT INTO `report` VALUES (3, 8, 1, '2024-05-20 10:13:44', 177, 61.5, '110/90', '3.5', '5.0', '125', '145', '40.5', '50.5', '4.5', '1.28', '1.11', '0.05', '18', '25', '10.5', '5.1', '阴性', '阴性', '阴性', '阴性', '5.5', '1.010', '320.0', '80.1', '6.50', '3.5', '3.0', '200.0');
INSERT INTO `report` VALUES (4, 9, 5, '2024-05-31 11:02:57', 172, 60, '135/88', '5.0', '4.80', '195', '140', '33', '55', '4.8', '1.52', '1.45', '2.98', '25', '28', '15.2', '5.9', '阴性', '阴性', '阴性', '阴性', '6.2', '1.018', '295.0', '72.3', '5.20', '4.0', '2.2', '120.5');
INSERT INTO `report` VALUES (5, 11, 5, '2024-06-01 09:33:14', 161, 50, '125/82', '4.0', '4.20', '180', '118', '44', '48', '5.5', '1.21', '1.60', '3.10', '22', '24', '11.8', '5.5', '阴性', '阴性', '阴性', '阴性', '5.8', '1.016', '265.0', '65.8', '4.90', '3.8', '2.0', '95.0');
INSERT INTO `report` VALUES (6, 17, 1, '2024-09-19 18:42:48', 180, 73, '140/90', '4', '4.5', '125', '130', '30', '50', '3.1', '0.56', '0.78', '2.0', '18', '20', '10.0', '5.2', '阴', '阴', '阴', '阳', '5', '1.010', '210', '62', '1.75', '7.0', '5.0', '15');
INSERT INTO `report` VALUES (7, 12, 6, '2024-10-10 10:00:00', 175, 70, '142/95', '6.1', '5.10', '160', '155', '35', '58', '6.2', '2.10', '1.20', '3.80', '35', '40', '20.5', '6.8', '阴性', '阴性', '阴性', '弱阳性', '6.8', '1.025', '450.0', '90.5', '8.20', '5.5', '4.5', '300.0');
INSERT INTO `report` VALUES (8, 9, 5, '2025-01-15 14:30:00', 172, 59, '128/84', '5.2', '4.90', '205', '142', '38', '52', '4.9', '1.48', '1.50', '2.85', '24', '26', '14.0', '5.7', '阴性', '阴性', '阴性', '阴性', '6.1', '1.019', '290.0', '70.1', '5.10', '3.9', '2.1', '115.0');

-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '角色',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of role
-- ----------------------------
INSERT INTO `role` VALUES (1, '管理员');
INSERT INTO `role` VALUES (2, '工作人员');
INSERT INTO `role` VALUES (3, '医护人员');
INSERT INTO `role` VALUES (4, '普通用户');

-- ----------------------------
-- Table structure for role_function
-- ----------------------------
DROP TABLE IF EXISTS `role_function`;
CREATE TABLE `role_function`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `rId` int NOT NULL COMMENT '角色id',
  `fId` int NOT NULL COMMENT '功能id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of role_function
-- ----------------------------
INSERT INTO `role_function` VALUES (1, 1, 1);
INSERT INTO `role_function` VALUES (2, 1, 2);
INSERT INTO `role_function` VALUES (3, 1, 3);
INSERT INTO `role_function` VALUES (4, 1, 4);
INSERT INTO `role_function` VALUES (6, 1, 6);
INSERT INTO `role_function` VALUES (7, 1, 7);
INSERT INTO `role_function` VALUES (8, 1, 8);
INSERT INTO `role_function` VALUES (9, 1, 9);
INSERT INTO `role_function` VALUES (10, 1, 10);
INSERT INTO `role_function` VALUES (11, 1, 11);
INSERT INTO `role_function` VALUES (12, 1, 12);
INSERT INTO `role_function` VALUES (13, 1, 13);
INSERT INTO `role_function` VALUES (14, 1, 14);
INSERT INTO `role_function` VALUES (15, 1, 15);
INSERT INTO `role_function` VALUES (16, 1, 16);
INSERT INTO `role_function` VALUES (17, 2, 1);
INSERT INTO `role_function` VALUES (18, 2, 2);
INSERT INTO `role_function` VALUES (19, 2, 3);
INSERT INTO `role_function` VALUES (20, 2, 7);
INSERT INTO `role_function` VALUES (21, 2, 8);
INSERT INTO `role_function` VALUES (22, 2, 9);
INSERT INTO `role_function` VALUES (23, 2, 10);
INSERT INTO `role_function` VALUES (24, 3, 4);
INSERT INTO `role_function` VALUES (25, 3, 11);
INSERT INTO `role_function` VALUES (26, 3, 16);
INSERT INTO `role_function` VALUES (29, 1, 17);
INSERT INTO `role_function` VALUES (30, 1, 18);
INSERT INTO `role_function` VALUES (32, 1, 20);
INSERT INTO `role_function` VALUES (33, 3, 17);
INSERT INTO `role_function` VALUES (34, 3, 18);
INSERT INTO `role_function` VALUES (36, 3, 20);
INSERT INTO `role_function` VALUES (37, 1, 21);
INSERT INTO `role_function` VALUES (38, 1, 22);

-- ----------------------------
-- Table structure for service_order
-- ----------------------------
DROP TABLE IF EXISTS `service_order`;
CREATE TABLE `service_order`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `uId` int NOT NULL COMMENT '用户编号',
  `mId` int NULL DEFAULT NULL COMMENT '社区工作者编号',
  `dId` int NULL DEFAULT NULL COMMENT '医护人员编号',
  `typeBId` int NULL DEFAULT NULL COMMENT '服务大类',
  `typeSId` int NULL DEFAULT NULL COMMENT '服务小类',
  `reserveDate` date NULL DEFAULT NULL COMMENT '预约日期',
  `serviceAddress` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务地点',
  `orderDetail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单详情',
  `orderDate` date NULL DEFAULT NULL COMMENT '下单日期',
  `orderTime` time NULL DEFAULT NULL COMMENT '下单时间',
  `acceptDate` date NULL DEFAULT NULL COMMENT '受理日期',
  `acceptTime` time NULL DEFAULT NULL COMMENT '受理时间',
  `finishDate` date NULL DEFAULT NULL COMMENT '完成日期',
  `finishTime` time NULL DEFAULT NULL COMMENT '完成时间',
  `rate` double NULL DEFAULT NULL COMMENT '订单评分',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '评价内容',
  `orderState` int NOT NULL DEFAULT 0 COMMENT '订单状态：0：待受理，1:已取消，2：待完成，3：待评价，4：已结束',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务人员姓名',
  `telephone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务人员电话',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of service_order
-- ----------------------------
INSERT INTO `service_order` VALUES (21, 17, 1, NULL, 1, 6, '2026-05-01', '家里', '尽快', '2026-04-30', '15:50:46', '2026-04-30', '15:51:43', NULL, NULL, NULL, NULL, 2, '测试', '10000000017');
INSERT INTO `service_order` VALUES (22, 17, NULL, NULL, 1, 6, '2026-04-30', '家里', NULL, '2026-04-30', '16:02:10', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL);

-- ----------------------------
-- Table structure for service_type
-- ----------------------------
DROP TABLE IF EXISTS `service_type`;
CREATE TABLE `service_type`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `serviceName` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务名称',
  `leaderId` int NULL DEFAULT NULL COMMENT '所属大类id',
  `image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `state` int NULL DEFAULT NULL COMMENT '状态：0：禁用，1：启用',
  `price` int NULL DEFAULT NULL COMMENT '价格',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 28 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of service_type
-- ----------------------------
INSERT INTO `service_type` VALUES (1, '医疗健康服务', NULL, '/image/20260327/1.png', 1, 20);
INSERT INTO `service_type` VALUES (2, '生活照料服务', NULL, '/image/20260327/2.png', 1, 0);
INSERT INTO `service_type` VALUES (3, '康复理疗服务', NULL, '/image/20260327/3.png', 1, 0);
INSERT INTO `service_type` VALUES (4, '心理慰藉服务', NULL, '/image/20260327/4.png', 1, 0);
INSERT INTO `service_type` VALUES (5, '文娱放松服务', NULL, '/image/20260327/5.png', 1, 0);
INSERT INTO `service_type` VALUES (6, '上门基础诊疗', 1, '/image/20260327/6.png', 1, 50);
INSERT INTO `service_type` VALUES (7, '血压血糖监测', 1, '/image/20260327/7.png', 1, 20);
INSERT INTO `service_type` VALUES (8, '用药指导管理', 1, '/image/20260327/8.png', 1, 30);
INSERT INTO `service_type` VALUES (9, '紧急医疗服务', 1, '/image/20260327/9.png', 1, 100);
INSERT INTO `service_type` VALUES (10, '上门助浴服务', 2, '/image/20260327/10.png', 1, 40);
INSERT INTO `service_type` VALUES (11, '居家清洁服务', 2, '/image/20260327/11.png', 1, 60);
INSERT INTO `service_type` VALUES (12, '衣物清洗服务', 2, '/image/20260327/12.png', 1, 30);
INSERT INTO `service_type` VALUES (13, '代购代办服务', 2, '/image/20260327/13.png', 1, 25);
INSERT INTO `service_type` VALUES (14, '营养配餐服务', 2, '/image/20260327/14.png', 1, 45);
INSERT INTO `service_type` VALUES (15, '康复训练指导', 3, '/image/20260327/15.png', 1, 80);
INSERT INTO `service_type` VALUES (16, '中医按摩理疗', 3, '/image/20260327/16.png', 1, 60);
INSERT INTO `service_type` VALUES (17, '肢体功能锻炼', 3, '/image/20260327/17.png', 1, 50);
INSERT INTO `service_type` VALUES (18, '疼痛管理服务', 3, '/image/20260327/18.png', 1, 70);
INSERT INTO `service_type` VALUES (19, '心理疏导咨询', 4, '/image/20260327/19.png', 1, 60);
INSERT INTO `service_type` VALUES (20, '情感陪伴聊天', 4, '/image/20260327/20.png', 1, 20);
INSERT INTO `service_type` VALUES (21, '认知训练服务', 4, '/image/20260327/21.png', 1, 40);
INSERT INTO `service_type` VALUES (22, '音乐疗愈服务', 4, '/image/20260327/22.png', 1, 35);
INSERT INTO `service_type` VALUES (23, '书法静心教学', 5, '/image/20260327/23.png', 1, 30);
INSERT INTO `service_type` VALUES (24, '园艺疗愈活动', 5, '/image/20260327/24.png', 1, 25);
INSERT INTO `service_type` VALUES (25, '手工制作教学', 5, '/image/20260327/25.png', 1, 20);
INSERT INTO `service_type` VALUES (26, '智能设备教学', 5, '/image/20260327/26.png', 1, 15);
INSERT INTO `service_type` VALUES (27, '阅读陪伴服务', 5, '/image/20260327/27.png', 1, 20);

-- ----------------------------
-- Table structure for sys_function
-- ----------------------------
DROP TABLE IF EXISTS `sys_function`;
CREATE TABLE `sys_function`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '功能名称',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '跳转路径',
  `fId` int NULL DEFAULT NULL COMMENT '父级id',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `sort` int NULL DEFAULT NULL COMMENT '排序',
  `state` int NULL DEFAULT NULL COMMENT '功能状态：0：禁用，1：启用',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_function
-- ----------------------------
INSERT INTO `sys_function` VALUES (1, '养老服务管理系统', NULL, 0, '', 0, NULL);
INSERT INTO `sys_function` VALUES (2, '活动管理', NULL, 1, 'GoldMedal', 1, 1);
INSERT INTO `sys_function` VALUES (3, '社区服务管理', NULL, 1, 'Brush', 2, 1);
INSERT INTO `sys_function` VALUES (4, '健康管理', NULL, 1, 'Bicycle', 3, 1);
INSERT INTO `sys_function` VALUES (6, '系统管理', NULL, 1, 'Operation', 5, 1);
INSERT INTO `sys_function` VALUES (7, '活动管理', 'ActivityMenageView', 2, NULL, 11, 1);
INSERT INTO `sys_function` VALUES (8, '活动类型管理', 'ActivityTypeView', 2, NULL, 12, 1);
INSERT INTO `sys_function` VALUES (9, '服务分类', 'ServiceTypeView', 3, NULL, 31, 1);
INSERT INTO `sys_function` VALUES (10, '订单管理', 'ServiceOrderView', 3, NULL, 32, 1);
INSERT INTO `sys_function` VALUES (11, '健康档案', 'HealthMenageView', 4, NULL, 42, 1);
INSERT INTO `sys_function` VALUES (12, '菜谱管理', NULL, 5, NULL, 51, 1);
INSERT INTO `sys_function` VALUES (13, '用户管理', 'UserMenageView', 6, NULL, 61, 1);
INSERT INTO `sys_function` VALUES (14, '工作人员管理', 'WorkerMenageView', 6, NULL, 62, 1);
INSERT INTO `sys_function` VALUES (15, '医护人员管理', 'DoctorMenageView', 6, NULL, 63, 1);
INSERT INTO `sys_function` VALUES (16, '留言板管理', 'ForumManageView', 6, 'ChatLineRound', 64, 1);
INSERT INTO `sys_function` VALUES (17, '菜谱管理', NULL, 1, 'Food', 5, 1);
INSERT INTO `sys_function` VALUES (18, '菜谱列表', '/RecipeManageView', 17, NULL, 51, 1);
INSERT INTO `sys_function` VALUES (20, '预订管理', '/RecipeOrderManageView', 17, NULL, 53, 1);
INSERT INTO `sys_function` VALUES (21, '智能服务运营', NULL, 1, 'Operation', 6, 1);
INSERT INTO `sys_function` VALUES (22, '小伴运营中心', '/AgentOperationsView', 21, NULL, 61, 1);

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `roleId` int NOT NULL COMMENT '用户角色：1：管理员，2：社区工作者，3：医护人员，4：普通用户',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '姓名',
  `sex` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '性别：1：男，2：女',
  `birthday` date NULL DEFAULT NULL COMMENT '出生日期',
  `age` int NULL DEFAULT NULL COMMENT '年龄',
  `idNum` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '身份证号码',
  `telephone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '住址',
  `department` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '科室',
  `point` int NULL DEFAULT NULL COMMENT '个人积分',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_username` (`username`) USING BTREE,
  UNIQUE INDEX `uk_user_telephone` (`telephone`) USING BTREE,
  UNIQUE INDEX `uk_user_id_num` (`idNum`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 1, 'admin', '$2b$12$ApzcgmwQ3tjoGeDRNP4i.O6PwAIJ2EJXrGfTdCz2f436ZalOgUTi6', '系统管理员', '男', '1985-01-01', 41, 'SYNTHETIC-ID-0012', '11111111111', '社区办公室', '管理中心', 9999);
INSERT INTO `user` VALUES (2, 2, 'ouwen', '$2b$12$wdBNkcm180oyiXs/Yvmzcupmaq6lM6/oVedzUCG.2eGfVdweRlgOC', '欧文', '男', '1993-03-03', 33, 'SYNTHETIC-ID-0016', '12111111111', '员工宿舍A栋', '社区服务部', 150);
INSERT INTO `user` VALUES (3, 2, 'pangke', '$2b$12$gDoVv8JsaWhxtf650jiyueTffFiGHyQnB5.bP9mymKKn5S9y1W1gG', '庞克', '男', '1990-02-02', 36, 'SYNTHETIC-ID-0014', '12222222222', '员工宿舍B栋', '社区服务部', 120);
INSERT INTO `user` VALUES (4, 4, 'cookie', '$2b$12$pz9J4U4ZdNYUWLbrRvjpYu1PxjftwLwACeZBKuzcwYVveIb9qC5ye', '王饼干', '女', '1952-03-03', 74, 'SYNTHETIC-ID-0003', '12333333333', '夕阳红小区5栋301', NULL, 45);
INSERT INTO `user` VALUES (5, 3, 'liuxing', '$2b$12$Jlf5seud82kb9bBYo.sFZewfrDPuQV/F9Ba8mkG0rFP0CxX8FI0hi', '刘星', '女', '1984-07-12', 41, 'SYNTHETIC-ID-0011', '10000000001', '社区医院宿舍', '全科', 200);
INSERT INTO `user` VALUES (6, 3, 'lisisi', '$2b$12$KMkPr4atzKjFqzbB7zS73ONZIxgyhvrQk5T/wTLcfH/Eyp1ZHZUqa', '李思思', '女', '1989-06-30', 36, 'SYNTHETIC-ID-0013', '10000000002', '社区医院宿舍', '心理科', 180);
INSERT INTO `user` VALUES (7, 3, 'xiangdong', '$2b$12$1gNdcKMDI.JdvTpxDs5CQeBR38SW3xhzoxv6NpQia36uHOXjEikfi', '向东', '男', '1978-11-17', 47, 'SYNTHETIC-ID-0009', '10000000003', '社区医院宿舍', '中医科', 220);
INSERT INTO `user` VALUES (8, 4, 'kangkai', '$2b$12$uAzo35jc8y0ERe1rTEyR/uJDLg7myl7F5AJQkbpZ3rN/rzzE5tbo.', '康凯', '男', '1951-10-10', 74, 'SYNTHETIC-ID-0002', '10000000008', '丰汇园1栋1层102', NULL, 26);
INSERT INTO `user` VALUES (9, 4, 'zhaoxiao', '$2b$12$nDK3P7CfuAwPQ7yLPShXpO.HRNDw9Eg3mtWkNxgkncjVFMLR05jT2', '赵晓', '女', '1963-03-09', 63, 'SYNTHETIC-ID-0007', '10000000009', '丰汇园2栋2层202', NULL, 38);
INSERT INTO `user` VALUES (10, 4, 'zhangshan', '$2b$12$2/D8v6RAcGh6rgFzRJaJx.qyxnGupFVW7x532HOBMeu0R6gxd.jdi', '章杉', '女', '1958-12-27', 67, 'SYNTHETIC-ID-0005', '10000000010', '丰汇园3栋3层303', NULL, 16);
INSERT INTO `user` VALUES (11, 4, 'tina1', '$2b$12$MIDl7hI8LTt6fJ2CDlhqJ.HltS1xYnCNevAgTOqMKpZW1kBtXpUvi', '缇娜', '女', '1965-11-12', 60, 'SYNTHETIC-ID-0008', '10000000011', '丰汇园4栋4层404', NULL, 26);
INSERT INTO `user` VALUES (12, 4, 'timou', '$2b$12$eUjxSJ4Lc15fT8Y0GgbY0.YtZkr6LXwrizr87m9Ki9GMC5k1X9.N.', '提明', '男', '1960-05-20', 65, 'SYNTHETIC-ID-0006', '10000000012', '丰汇园5栋5层505', NULL, 17);
INSERT INTO `user` VALUES (13, 4, 'laozhang', '$2b$12$CS3qI6OD.bKet5lsa4X.j.B1hIJbYMWX0VSuCkC2LqoJ8qbFPnxwS', '张卫国', '男', '1955-08-15', 70, 'SYNTHETIC-ID-0004', '10000000004', '千禧小区1栋101', NULL, 5);
INSERT INTO `user` VALUES (14, 4, 'test1', '$2b$12$dZclZnq6uMM9BrJLaESuIOeAE2oNoMTodekoAe.RG978hLA9M.Idq', '测试1', '女', '1949-10-01', 76, 'SYNTHETIC-ID-0001', '10000000014', '测试地址1', NULL, 0);
INSERT INTO `user` VALUES (15, 3, 'yihutest1', '$2b$12$Gg24MMFCJHFt3Rxn747df.xzJys67O2vk7E5/5kJJIUSwjuaFFAIK', '医护测试1', '男', '1980-12-12', 45, 'SYNTHETIC-ID-0010', '10000000013', '社区医院', '检验科', 50);
INSERT INTO `user` VALUES (16, 2, 'test2', '$2b$12$5IhJ8jZgIYQBEUkNQ/.wVOKLmUcRjTlgH2XwTuxGKPHW2n08/Ifae', '社区测试员', '女', '1995-05-05', 31, 'SYNTHETIC-ID-0017', '10000000005', '员工宿舍C栋', '综合部', 80);
INSERT INTO `user` VALUES (17, 4, 'linge', '$2b$12$noqva/TN11PphoBRg9HuC.RFcRNtwe3bnFh.CNQgi9YKQtWVMbFoy', '林格', '男', '2000-09-23', 25, 'SYNTHETIC-ID-0018', '10000000015', '千禧小区3栋502', NULL, 20);
INSERT INTO `user` VALUES (18, 2, 'yange', '$2b$12$cyfw72x9WExEj5.0jOI5UOA0YXE9JS6fKVietbIE1gwGDqr8pZqdW', '杨戈', '男', '1992-11-11', 33, 'SYNTHETIC-ID-0015', '10000000016', '员工宿舍A栋', '社区服务部', 100);

-- ----------------------------
-- Table structure for user_activity
-- ----------------------------
DROP TABLE IF EXISTS `user_activity`;
CREATE TABLE `user_activity`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `uId` int NULL DEFAULT NULL COMMENT '用户id',
  `aId` int NULL DEFAULT NULL COMMENT '活动id',
  `enterDate` date NULL DEFAULT NULL COMMENT '报名日期',
  `enterTime` time NULL DEFAULT NULL COMMENT '报名时间',
  `state` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '已取消报名、报名审核中或报名成功',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_activity`(`uId` ASC, `aId` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 36 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_activity
-- ----------------------------
INSERT INTO `user_activity` VALUES (1, 8, 1, '2024-04-08', '15:41:25', '0');
INSERT INTO `user_activity` VALUES (2, 8, 2, '2024-04-22', '13:41:43', '2');
INSERT INTO `user_activity` VALUES (3, 8, 3, '2024-02-25', '12:01:00', '2');
INSERT INTO `user_activity` VALUES (4, 8, 5, '2024-05-12', '12:15:54', '2');
INSERT INTO `user_activity` VALUES (5, 9, 1, '2024-05-12', '12:20:27', '2');
INSERT INTO `user_activity` VALUES (6, 9, 2, '2024-05-12', '12:41:15', '2');
INSERT INTO `user_activity` VALUES (7, 9, 5, '2024-05-12', '12:43:44', '2');
INSERT INTO `user_activity` VALUES (8, 9, 6, '2024-05-12', '12:46:08', '2');
INSERT INTO `user_activity` VALUES (9, 10, 1, '2024-05-12', '15:20:21', '2');
INSERT INTO `user_activity` VALUES (10, 10, 3, '2024-05-12', '15:21:53', '0');
INSERT INTO `user_activity` VALUES (11, 10, 5, '2024-05-12', '15:22:17', '2');
INSERT INTO `user_activity` VALUES (12, 11, 3, '2024-05-12', '15:23:00', '2');
INSERT INTO `user_activity` VALUES (13, 12, 1, '2024-05-12', '15:30:34', '2');
INSERT INTO `user_activity` VALUES (14, 11, 6, '2024-05-16', '17:46:43', '0');
INSERT INTO `user_activity` VALUES (15, 12, 4, '2024-04-10', '09:13:01', '2');
INSERT INTO `user_activity` VALUES (16, 11, 4, '2024-04-19', '10:14:57', '2');
INSERT INTO `user_activity` VALUES (17, 9, 7, '2024-05-27', '00:10:02', '2');
INSERT INTO `user_activity` VALUES (18, 11, 7, '2024-05-29', '10:10:21', '2');
INSERT INTO `user_activity` VALUES (19, 10, 7, '2024-05-30', '11:32:31', '2');
INSERT INTO `user_activity` VALUES (20, 12, 7, '2024-05-30', '12:32:52', '2');
INSERT INTO `user_activity` VALUES (21, 10, 6, '2024-06-01', '00:35:15', '2');
INSERT INTO `user_activity` VALUES (22, 8, 6, '2024-05-31', '10:41:07', '2');
INSERT INTO `user_activity` VALUES (23, 8, 8, '2024-06-01', '09:25:05', '2');
INSERT INTO `user_activity` VALUES (24, 17, 11, '2024-09-19', '18:40:33', '2');

UPDATE `user_activity`
SET `state` = CASE `state`
  WHEN '0' THEN '已取消报名'
  WHEN '1' THEN '报名审核中'
  WHEN '2' THEN '报名成功'
  ELSE `state`
END;

UPDATE `activity` a
LEFT JOIN (
  SELECT `aId`, COUNT(*) AS `confirmed_count`
  FROM `user_activity`
  WHERE `state` = '报名成功'
  GROUP BY `aId`
) registrations ON registrations.`aId` = a.`id`
SET a.`signNum` = COALESCE(registrations.`confirmed_count`, 0);

-- Active-domain referential integrity and query indexes.
ALTER TABLE `user`
  ADD CONSTRAINT `fk_user_role` FOREIGN KEY (`roleId`) REFERENCES `role` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `activity`
  ADD CONSTRAINT `fk_activity_type` FOREIGN KEY (`activityTypeId`) REFERENCES `activity_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_activity_director` FOREIGN KEY (`dId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `user_activity`
  ADD CONSTRAINT `fk_user_activity_user` FOREIGN KEY (`uId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_user_activity_activity` FOREIGN KEY (`aId`) REFERENCES `activity` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `service_type`
  ADD CONSTRAINT `fk_service_type_parent` FOREIGN KEY (`leaderId`) REFERENCES `service_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `service_order`
  ADD CONSTRAINT `fk_service_order_user` FOREIGN KEY (`uId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_service_order_worker` FOREIGN KEY (`mId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_service_order_doctor` FOREIGN KEY (`dId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_service_order_parent_type` FOREIGN KEY (`typeBId`) REFERENCES `service_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_service_order_child_type` FOREIGN KEY (`typeSId`) REFERENCES `service_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `report`
  ADD CONSTRAINT `fk_report_user` FOREIGN KEY (`uId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_report_doctor` FOREIGN KEY (`dId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `comment`
  ADD CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_comment_reply_user` FOREIGN KEY (`reply_to`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE `role_function`
  ADD CONSTRAINT `fk_role_function_role` FOREIGN KEY (`rId`) REFERENCES `role` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_role_function_function` FOREIGN KEY (`fId`) REFERENCES `sys_function` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

CREATE INDEX `idx_activity_state_schedule` ON `activity` (`state`, `activityDate`, `startTime`, `endTime`);
CREATE INDEX `idx_user_activity_activity_state` ON `user_activity` (`aId`, `state`);
CREATE INDEX `idx_user_activity_user_state` ON `user_activity` (`uId`, `state`);
CREATE INDEX `idx_service_order_user_state` ON `service_order` (`uId`, `orderState`);
CREATE INDEX `idx_service_order_type_state` ON `service_order` (`typeBId`, `orderState`);
CREATE INDEX `idx_report_user_time` ON `report` (`uId`, `time`);

-- ----------------------------
-- View structure for distinct_menu_view
-- ----------------------------
DROP VIEW IF EXISTS `distinct_menu_view`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `distinct_menu_view` AS select distinct `s`.`id` AS `id`,`s`.`name` AS `name`,`s`.`path` AS `path`,`s`.`fId` AS `fId`,`s`.`icon` AS `icon`,`s`.`sort` AS `sort`,`s`.`state` AS `state` from (`sys_function` `s` join `role_function` `r` on((`s`.`id` = `r`.`fId`))) where (`s`.`state` = 1) order by `s`.`sort`;

-- ----------------------------
-- Triggers structure for table comment
-- ----------------------------
DROP TRIGGER IF EXISTS `comment_before_insert`;
delimiter ;;
CREATE TRIGGER `comment_before_insert` BEFORE INSERT ON `comment` FOR EACH ROW BEGIN
    -- 从user表获取用户信息
    DECLARE v_user_name VARCHAR(255);
    DECLARE v_user_telephone VARCHAR(255);
    DECLARE v_reply_to_name VARCHAR(255);

    -- 获取留言用户的信息
    SELECT name, telephone INTO v_user_name, v_user_telephone
    FROM user
    WHERE id = NEW.user_id;

    -- 设置留言用户的信息
    SET NEW.user_name = v_user_name;
    SET NEW.user_telephone = v_user_telephone;

    -- 如果有回复对象，获取被回复用户的信息
    IF NEW.reply_to IS NOT NULL THEN
        SELECT name INTO v_reply_to_name
        FROM user
        WHERE id = NEW.reply_to;

        SET NEW.reply_to_name = v_reply_to_name;
    END IF;
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table user
-- ----------------------------
DROP TRIGGER IF EXISTS `user_after_update`;
delimiter ;;
CREATE TRIGGER `user_after_update` AFTER UPDATE ON `user` FOR EACH ROW BEGIN
    -- 更新comment表中的用户信息
    UPDATE comment
    SET
        user_name = NEW.name,
        user_telephone = NEW.telephone
    WHERE user_id = NEW.id;

    -- 更新comment表中的回复对象信息
    UPDATE comment
    SET reply_to_name = NEW.name
    WHERE reply_to = NEW.id;
END
;;
delimiter ;

-- Every non-empty seeded image URL above resolves to a real raster file under
-- the repository's image/ directory. scripts/validate-seed-assets.ps1 keeps
-- this contract fail-fast for both Local and Docker startup modes.

SET FOREIGN_KEY_CHECKS = 1;
