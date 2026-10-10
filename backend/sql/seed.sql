-- =============================================================================
-- 联调种子数据（幂等，可反复执行）
-- 建库 + 建 5 张表 + 灌种子数据；miaosha_goods 时间窗基于 NOW() 相对计算，
-- 每次执行重新对齐到当前时间前后，保证进行中 / 未开始 / 已结束各至少 1 个。
-- 表结构见 docs/db-design.md 第 3 节。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS miaosha
  DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE miaosha;

-- 会话时区切到东八区，与后端 JDBC serverTimezone=Asia/Shanghai 对齐
SET time_zone = '+08:00';

-- 建表 ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goods (
  id           BIGINT(20) NOT NULL AUTO_INCREMENT,
  goods_name   VARCHAR(16)  DEFAULT NULL,
  goods_title  VARCHAR(64)  DEFAULT NULL,
  goods_img    VARCHAR(64)  DEFAULT NULL,
  goods_detail LONGTEXT,
  goods_price  DECIMAL(10,2) DEFAULT NULL,
  goods_stock  INT(11)      DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS miaosha_goods (
  id            BIGINT(20) NOT NULL AUTO_INCREMENT,
  goods_id      BIGINT(20) DEFAULT NULL,
  miaosha_price DECIMAL(10,2) DEFAULT NULL,
  stock_count   INT(11)    DEFAULT NULL,
  start_date    DATETIME   DEFAULT NULL,
  end_date      DATETIME   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS miaosha_order (
  id       BIGINT(20) NOT NULL AUTO_INCREMENT,
  user_id  BIGINT(20) DEFAULT NULL,
  order_id BIGINT(20) DEFAULT NULL,
  goods_id BIGINT(20) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY u_uid_gid (user_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS miaosha_user (
  id              BIGINT(20) NOT NULL,
  nickname        VARCHAR(255) DEFAULT NULL,
  password        VARCHAR(32)  DEFAULT NULL,
  salt            VARCHAR(10)  DEFAULT NULL,
  head            VARCHAR(128) DEFAULT NULL,
  register_date   DATETIME     DEFAULT NULL,
  last_login_date DATETIME     DEFAULT NULL,
  login_count     INT(11)      DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_info (
  id               BIGINT(20) NOT NULL AUTO_INCREMENT,
  user_id          BIGINT(20) DEFAULT NULL,
  goods_id         BIGINT(20) DEFAULT NULL,
  delivery_addr_id BIGINT(20) DEFAULT NULL,
  goods_name       VARCHAR(16) DEFAULT NULL,
  goods_count      INT(11)     DEFAULT NULL,
  goods_price      DECIMAL(10,2) DEFAULT NULL,
  order_channel    TINYINT(4)  DEFAULT NULL,
  status           TINYINT(4)  DEFAULT NULL,
  create_date      DATETIME    DEFAULT NULL,
  pay_date         DATETIME    DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品 ----------------------------------------------------------------------
INSERT IGNORE INTO goods
  (id, goods_name, goods_title, goods_img, goods_price, goods_stock)
VALUES
  (1, 'iphoneX', 'Apple iPhone X (A1865) 64GB 银色 移动联通电信4G手机', '/img/iphonex.png', 8765.00, 10000),
  (2, '华为Meta9', '华为 Mate 9 4GB+32GB版 月光银 移动联通电信4G手机 双卡双待', '/img/meta10.png', 3212.00, 10000),
  (3, 'iphone8', 'Apple iPhone 8 (A1865) 64GB 银色 移动联通电信4G手机', '/img/iphone8.png', 5589.00, 10000),
  (4, '小米6', '小米6 4GB+32GB版 月光银 移动联通电信4G手机 双卡双待', '/img/mi6.png', 3212.00, 10000);

-- 秒杀活动：进行中×2（商品1、2）、未开始×1（商品3）、已结束×1（商品4）
INSERT INTO miaosha_goods
  (id, goods_id, miaosha_price, stock_count, start_date, end_date)
VALUES
  (1, 1, 0.01, 9, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 1 DAY),
  (2, 2, 0.01, 9, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 2 DAY),
  (3, 3, 0.01, 9, NOW() + INTERVAL 1 HOUR, NOW() + INTERVAL 1 DAY),
  (4, 4, 0.01, 9, NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE
  miaosha_price = VALUES(miaosha_price),
  stock_count   = VALUES(stock_count),
  start_date    = VALUES(start_date),
  end_date      = VALUES(end_date);

-- 测试用户
INSERT IGNORE INTO miaosha_user
  (id, nickname, password, salt, register_date)
VALUES
  (18912341234, 'Joshua', 'b7797cce01b4b131b433b6acf4add449', '1a2b3c4d', NOW());

