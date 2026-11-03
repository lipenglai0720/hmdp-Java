-- AI skill tables and initial skills, extracted from the project db.sql.
-- Idempotent supplement for an existing hmdp database.
-- Existing rows are preserved by CREATE TABLE IF NOT EXISTS and INSERT IGNORE.
SET NAMES utf8mb4;
USE `hmdp`;

CREATE TABLE IF NOT EXISTS `tb_ai_skill` (
                               `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                               `code` varchar(64) NOT NULL COMMENT '技能编码',
                               `name` varchar(64) NOT NULL COMMENT '技能名称',
                               `scene` varchar(32) NOT NULL DEFAULT 'merchant' COMMENT 'consumer/merchant/admin',
                               `description` varchar(255) DEFAULT NULL COMMENT '技能说明',
                               `input_schema` text COMMENT '输入字段JSON说明',
                               `prompt_template` text NOT NULL COMMENT '固定提示词模板',
                               `output_schema` text COMMENT '输出结构JSON说明',
                               `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                               `sort` int(8) NOT NULL DEFAULT 0 COMMENT '排序值',
                               `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`) USING BTREE,
                               UNIQUE KEY `uk_ai_skill_code` (`code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI技能定义表';

CREATE TABLE IF NOT EXISTS `tb_ai_skill_run` (
                                   `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                                   `skill_code` varchar(64) NOT NULL COMMENT '技能编码',
                                   `user_id` bigint(20) unsigned NOT NULL COMMENT '调用用户',
                                   `merchant_id` bigint(20) unsigned DEFAULT NULL COMMENT '商家ID',
                                   `input_json` text COMMENT '输入JSON',
                                   `output_json` mediumtext COMMENT '输出JSON',
                                   `status` varchar(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行状态',
                                   `error_msg` varchar(255) DEFAULT NULL COMMENT '错误信息',
                                   `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   PRIMARY KEY (`id`) USING BTREE,
                                   KEY `idx_ai_skill_run_user` (`user_id`, `create_time`) USING BTREE,
                                   KEY `idx_ai_skill_run_code` (`skill_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI技能调用记录表';

INSERT IGNORE INTO `tb_ai_skill` (`code`, `name`, `scene`, `description`, `input_schema`, `prompt_template`, `output_schema`, `enabled`, `sort`) VALUES
('blog_draft', 'AI写探店笔记', 'consumer', '根据店铺和关键词生成可编辑的探店笔记草稿。', '{\"shopId\":\"店铺ID，可选\",\"shopName\":\"店铺名称\",\"keywords\":\"体验关键词\",\"tone\":\"文风\"}', '你是本地生活内容编辑。请为${shopName}写一篇可编辑的探店笔记草稿，文风为${tone}，重点包含${keywords}。要求有标题、正文、推荐理由和避坑提醒。', '{\"title\":\"标题\",\"content\":\"正文\",\"tips\":\"提醒\"}', 1, 10),
('review_summary', 'AI总结店铺评价', 'merchant', '将评价内容总结成优点、问题和适合人群。', '{\"shopId\":\"店铺ID，可选\",\"shopName\":\"店铺名称\",\"keywords\":\"评价关键词或原文\"}', '你是商家运营助理。请基于${shopName}的评价信息，总结优点、待优化问题、适合人群和运营建议。评价信息：${keywords}。', '{\"advantages\":[],\"problems\":[],\"audience\":[],\"suggestions\":[]}', 1, 20),
('review_reply', 'AI生成评价回复', 'merchant', '为好评或差评生成礼貌、可直接编辑的商家回复。', '{\"shopName\":\"店铺名称\",\"review\":\"用户评价\",\"attitude\":\"回复语气\"}', '你是商家客服。请针对${shopName}的这条评价生成回复，语气为${attitude}。评价：${review}。不要承诺无法确认的赔偿或事实。', '{\"reply\":\"回复内容\"}', 1, 30),
('coupon_copy', 'AI生成优惠券文案', 'merchant', '生成优惠券标题、副标题和活动推广话术。', '{\"shopName\":\"店铺名称\",\"discount\":\"优惠力度\",\"target\":\"目标人群\",\"keywords\":\"卖点\"}', '你是本地生活营销策划。请为${shopName}生成优惠券标题、副标题和推广话术。优惠：${discount}，目标人群：${target}，卖点：${keywords}。', '{\"title\":\"券标题\",\"subtitle\":\"副标题\",\"copy\":\"推广话术\"}', 1, 40),
('shop_tags', 'AI提取店铺标签', 'merchant', '根据店铺信息提炼用于推荐和搜索的标签。', '{\"shopName\":\"店铺名称\",\"keywords\":\"店铺描述、评价或卖点\"}', '请从${shopName}的描述中提取8个以内短标签，用于本地生活推荐和搜索。描述：${keywords}。', '{\"tags\":[]}', 1, 50),
('merchant_daily_report', 'AI生成经营日报', 'merchant', '把商家经营数据转换成日报摘要。', '{\"shopName\":\"店铺名称\",\"keywords\":\"今日数据或观察\"}', '你是商家经营分析师。请为${shopName}生成一份简短经营日报，包含今日表现、问题提醒和明日动作建议。数据：${keywords}。', '{\"summary\":\"摘要\",\"risks\":[],\"actions\":[]}', 1, 60),
('short_video_script', 'AI生成短视频脚本', 'merchant', '生成本地生活探店短视频脚本。', '{\"shopName\":\"店铺名称\",\"keywords\":\"卖点\",\"duration\":\"视频时长\"}', '请为${shopName}生成一个${duration}短视频探店脚本，卖点：${keywords}。输出分镜、口播和结尾引导。', '{\"shots\":[],\"voiceover\":\"口播\",\"ending\":\"结尾\"}', 1, 70),
('promotion_plan', 'AI生成活动方案草稿', 'merchant', '生成可由商家确认后执行的活动方案草稿。', '{\"shopName\":\"店铺名称\",\"goal\":\"活动目标\",\"budget\":\"预算\",\"keywords\":\"主推产品或卖点\"}', '你是本地生活活动策划。请为${shopName}生成活动方案草稿。目标：${goal}，预算：${budget}，主推：${keywords}。只给草稿，不要声称已执行。', '{\"theme\":\"活动主题\",\"steps\":[],\"copy\":\"宣传文案\"}', 1, 80);
