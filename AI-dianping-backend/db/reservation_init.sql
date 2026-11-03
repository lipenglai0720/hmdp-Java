-- 补齐预约表。可重复执行；不会删除预约记录或业务表。
SET NAMES utf8mb4;
USE hmdp;

CREATE TABLE IF NOT EXISTS tb_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    user_phone VARCHAR(11) NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    reservation_time DATETIME NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待确认，1已确认，2已取消，3商家拒绝',
    num_people INT NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active_slot DATETIME GENERATED ALWAYS AS (IF(status IN (0,1), reservation_time, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_shop_active_slot (user_id, shop_id, active_slot),
    KEY idx_shop_id (shop_id),
    KEY idx_user_phone (user_phone),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES tb_user(id),
    CONSTRAINT fk_reservation_shop FOREIGN KEY (shop_id) REFERENCES tb_shop(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺预约表';

CREATE TABLE IF NOT EXISTS tb_shop_manager (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_manager_user_shop (user_id, shop_id),
    KEY idx_manager_shop (shop_id),
    CONSTRAINT fk_manager_user FOREIGN KEY (user_id) REFERENCES tb_user(id),
    CONSTRAINT fk_manager_shop FOREIGN KEY (shop_id) REFERENCES tb_shop(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺预约管理权限';

-- 兼容旧 db.sql 创建的预约表，先建立新的唯一索引，再移除旧索引。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='tb_reservation' AND column_name='active_slot')=0,
    'ALTER TABLE tb_reservation ADD COLUMN active_slot DATETIME GENERATED ALWAYS AS (IF(status IN (0,1), reservation_time, NULL)) STORED',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='tb_reservation' AND index_name='uk_user_shop_active_slot')=0,
    'ALTER TABLE tb_reservation ADD UNIQUE KEY uk_user_shop_active_slot (user_id, shop_id, active_slot)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='tb_reservation' AND index_name='uk_user_shop_time')>0,
    'ALTER TABLE tb_reservation DROP INDEX uk_user_shop_time',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
