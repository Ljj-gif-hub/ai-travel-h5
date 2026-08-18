package org.example.traveljava.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 【新功能】慢查询监控：实现 Hibernate StatementInspector，
 * 对执行时间超过 1000ms 的 SQL 打 warn 日志（含耗时与 SQL 摘要前 300 字符）。
 *
 * 启用方式（application.yml）：
 *   spring.jpa.properties.hibernate.session_factory.statement_inspector: org.example.traveljava.config.SqlTimingInspector
 *
 * 说明：该类由 Hibernate 直接实例化（非 Spring Bean），必须有默认构造器。
 */
public class SqlTimingInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(SqlTimingInspector.class);

    /** 慢查询阈值：1000ms */
    private static final long SLOW_THRESHOLD_MS = 1000L;
    /** 日志中 SQL 摘要最大长度 */
    private static final int SUMMARY_LEN = 300;

    /** 每条语句执行的起始时间（线程内串行执行） */
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
        if (sql == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Long start = startTime.get();
        if (start == null) {
            // 本线程第一条语句：只记录起点
            startTime.set(now);
            return sql;
        }
        long elapsed = now - start;
        startTime.set(now);
        if (elapsed > SLOW_THRESHOLD_MS) {
            String summary = sql.replaceAll("\\s+", " ");
            if (summary.length() > SUMMARY_LEN) {
                summary = summary.substring(0, SUMMARY_LEN) + "...";
            }
            log.warn("慢查询 SQL: 耗时={}ms, SQL={}", elapsed, summary);
        }
        return sql;
    }
}
