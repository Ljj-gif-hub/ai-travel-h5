-- ================================================================
-- V4: MQ 审计幂等加固（MySQL 方言）
--
-- ⚠️ 本脚本仅供生产环境（prod）手动执行！
-- prod 的 spring.jpa.hibernate.ddl-auto=none，不会自动建表/加约束，
-- 因此所有新增约束必须由本脚本手动落库。
-- dev 的 ddl-auto=update 会对新表自动建唯一约束；对已存在的表不会自动加约束，
-- dev 若要复现同样兜底可手动执行本脚本（本脚本本身幂等）。
--
-- 内容：
--   async_audit.event_id 唯一约束 uk_async_audit_event_id
--   幂等消费的 DB 层兜底：并发 redelivery 重复插行时由数据库拒绝
--   （配合 OrderPaidEventConsumer 对 DataIntegrityViolationException 的 ACK 跳过处理）
--   [MQ-1]
-- ================================================================

DROP PROCEDURE IF EXISTS sp_add_uk_async_audit_event_id;
DELIMITER $$
CREATE PROCEDURE sp_add_uk_async_audit_event_id()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'async_audit' AND index_name = 'uk_async_audit_event_id'
    ) THEN
        -- 清理历史重复 event_id（若有），保留最早一条（id 最小），避免加约束时因历史脏数据失败
        DELETE a1 FROM async_audit a1
        INNER JOIN async_audit a2 ON a1.event_id = a2.event_id AND a1.id > a2.id;
        ALTER TABLE async_audit ADD UNIQUE KEY uk_async_audit_event_id (event_id);
    END IF;
END$$
DELIMITER ;

CALL sp_add_uk_async_audit_event_id();

DROP PROCEDURE IF EXISTS sp_add_uk_async_audit_event_id;
