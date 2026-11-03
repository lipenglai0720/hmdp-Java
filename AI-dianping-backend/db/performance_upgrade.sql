-- 已有库的增量升级：仅增加约束和查询索引，不删除记录，可重复执行。
-- 若历史订单或关注存在重复，唯一约束会报错停止；请先人工核对重复记录。
SET NAMES utf8mb4;
SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_voucher_order' AND index_name='uk_voucher_order_user_voucher'),'SELECT 1','ALTER TABLE tb_voucher_order ADD UNIQUE KEY uk_voucher_order_user_voucher (user_id,voucher_id)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_follow' AND index_name='uk_follow_user_target'),'SELECT 1','ALTER TABLE tb_follow ADD UNIQUE KEY uk_follow_user_target (user_id,follow_user_id)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_blog' AND index_name='idx_blog_shop_created'),'SELECT 1','ALTER TABLE tb_blog ADD KEY idx_blog_shop_created (shop_id,create_time,id)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_blog' AND index_name='idx_blog_author_created'),'SELECT 1','ALTER TABLE tb_blog ADD KEY idx_blog_author_created (user_id,create_time,id)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_voucher_order' AND index_name='idx_order_user_created'),'SELECT 1','ALTER TABLE tb_voucher_order ADD KEY idx_order_user_created (user_id,create_time,id)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_reservation' AND index_name='idx_reservation_shop_status_time'),'SELECT 1','ALTER TABLE tb_reservation ADD KEY idx_reservation_shop_status_time (shop_id,status,reservation_time)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_reservation' AND index_name='idx_reservation_user_created'),'SELECT 1','ALTER TABLE tb_reservation ADD KEY idx_reservation_user_created (user_id,create_time,id)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @upgrade_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_blog_comments' AND index_name='idx_comment_blog_status_created'),'SELECT 1','ALTER TABLE tb_blog_comments ADD KEY idx_comment_blog_status_created (blog_id,status,create_time,id)');
PREPARE upgrade_statement FROM @upgrade_ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

