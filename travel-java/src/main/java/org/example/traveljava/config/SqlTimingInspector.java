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
 *
 * L-SQL-1 修复：Hibernate 6.4 的 StatementInspector 只在执行前回调（无 after-execution 钩子），
 * 原实现把「上一条间隔」的耗时打印在「下一条 SQL」上（张冠李戴）。
 * 现改为：本次 inspect 时结算上一条语句，SQL 与耗时同属一条语句，归属正确；
 * 上一条 Entry 被本条覆盖，ThreadLocal 不累积。
 */
public class SqlTimingInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(SqlTimingInspector.class);

    /** 慢查询阈值：1000ms */
    private static final long SLOW_THRESHOLD_MS = 1000L;
    /** 日志中 SQL 摘要最大长度 */
    private static final int SUMMARY_LEN = 300;

    /** 最近一条语句的起点时间 + SQL 文本（线程内串行执行） */
    private static final class LastStmt {
        final long start;
        final String sql;
        LastStmt(long start, String sql) {
            this.start = start;
            this.sql = sql;
        }
    }

    private final ThreadLocal<LastStmt> lastStmt = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
        if (sql == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        LastStmt prev = lastStmt.get();
        // 自替换：上一条 Entry 被本条覆盖，ThreadLocal 不累积、无线程内泄漏
        lastStmt.set(new LastStmt(now, sql));
        if (prev != null) {
            long elapsed = now - prev.start;
            if (elapsed > SLOW_THRESHOLD_MS) {
                log.warn("慢查询 SQL: 耗时={}ms, SQL={}", elapsed, summarize(prev.sql));
            }
        }
        return sql;
    }

    private static String summarize(String sql) {
        String summary = sql.replaceAll("\\s+", " ");
        if (summary.length() > SUMMARY_LEN) {
            summary = summary.substring(0, SUMMARY_LEN) + "...";
        }
        return summary;
    }
}
