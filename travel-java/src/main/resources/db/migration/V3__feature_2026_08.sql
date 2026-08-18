-- ================================================================
-- V3: 2026-08 新功能数据表初始化（MySQL 方言）
--
-- ⚠️ 本脚本仅供生产环境（prod）手动执行！
-- prod 的 spring.jpa.hibernate.ddl-auto=none，不会自动建表/加列，
-- 因此所有新增实体/字段必须由本脚本手动落库。
-- dev 的 ddl-auto=update 会自动建表/加列，若在 dev 执行本脚本会因
-- 表/列已存在而报错——dev 不需要执行本脚本。
--
-- 包含：
--   1. trip_shares       行程分享（24h 有效 token，可撤销）
--   2. trip_templates    行程模板市场（可实例化）
--   3. reports           内容举报（≥5 次自动隐藏）
--   4. refunds           退款单（审核流）
--   5. invoices          发票（一单一票）
--   6. note_collections  游记收藏夹（noteIds JSON LONGTEXT）
--   7. notes/posts/comments 追加 hidden 列（举报隐藏）
--   8. users 追加 points 列（积分等级）
-- ================================================================

-- ----------------------------
-- 1. 行程分享表
-- ----------------------------
CREATE TABLE IF NOT EXISTS trip_shares (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token       VARCHAR(64)     NOT NULL,
    plan_id     BIGINT          NOT NULL,
    user_id     BIGINT          NOT NULL,
    expire_at   DATETIME        NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_trip_shares_token (token),
    KEY idx_trip_shares_token (token),
    KEY idx_trip_shares_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 2. 行程模板表
-- ----------------------------
CREATE TABLE IF NOT EXISTS trip_templates (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    destination VARCHAR(50)     NOT NULL,
    days        INT             NULL,
    budget      BIGINT          NULL,
    people      INT             NULL,
    cover_image TEXT            NULL,
    tags        VARCHAR(200)    NULL,
    description VARCHAR(500)    NULL,
    downloads   INT             NOT NULL DEFAULT 0,
    plan_json   LONGTEXT        NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'published',
    creator_id  BIGINT          NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NULL,
    KEY idx_trip_templates_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 3. 内容举报表
-- ----------------------------
CREATE TABLE IF NOT EXISTS reports (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT          NOT NULL,
    target_type VARCHAR(20)     NOT NULL,
    target_id   BIGINT          NOT NULL,
    reason      VARCHAR(200)    NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'pending',
    handled_by  BIGINT          NULL,
    handled_at  DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_report_once (reporter_id, target_type, target_id),
    KEY idx_reports_target (target_type, target_id),
    KEY idx_reports_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 4. 退款单表
-- ----------------------------
CREATE TABLE IF NOT EXISTS refunds (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT          NOT NULL,
    user_id     BIGINT          NOT NULL,
    amount      BIGINT          NOT NULL,
    reason      VARCHAR(200)    NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'pending',
    refund_no   VARCHAR(64)     NULL,
    handled_by  BIGINT          NULL,
    handled_at  DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NULL,
    KEY idx_refunds_user_id (user_id),
    KEY idx_refunds_status (status),
    KEY idx_refunds_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 5. 发票表（一单一票）
-- ----------------------------
CREATE TABLE IF NOT EXISTS invoices (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT          NOT NULL,
    user_id     BIGINT          NOT NULL,
    invoice_no  VARCHAR(32)     NOT NULL,
    title       VARCHAR(200)    NOT NULL,
    tax_no      VARCHAR(50)     NULL,
    type        VARCHAR(20)     NULL DEFAULT 'personal',
    amount      BIGINT          NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invoices_order (order_id),
    UNIQUE KEY uk_invoices_no (invoice_no),
    KEY idx_invoices_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 6. 游记收藏夹表
-- ----------------------------
CREATE TABLE IF NOT EXISTS note_collections (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    NULL,
    cover_image VARCHAR(500)    NULL,
    note_ids    LONGTEXT        NOT NULL,
    is_public   TINYINT(1)      NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NULL,
    KEY idx_collections_user_id (user_id),
    KEY idx_collections_public (is_public, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- 7. 追加 hidden 列（举报隐藏）
-- ⚠️ 若 prod 库已存在该列（曾手工加过），请跳过对应语句。
-- ----------------------------
ALTER TABLE notes    ADD COLUMN hidden TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE posts    ADD COLUMN hidden TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE comments ADD COLUMN hidden TINYINT(1) NOT NULL DEFAULT 0;

-- ----------------------------
-- 8. users 追加 points 列（积分等级）
-- ⚠️ 若 prod 库 users 已存在 points 列，请跳过该语句。
--    （实体 User.points 长期存在，prod 首次上线前的库可能已有此列）
-- ----------------------------
ALTER TABLE users ADD COLUMN points INT NOT NULL DEFAULT 0;
