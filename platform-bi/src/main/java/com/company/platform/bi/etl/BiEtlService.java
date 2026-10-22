package com.company.platform.bi.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BI 定时抽取服务。
 *
 * <p>V1.4 要求经营指标 5–15 分钟刷新一次。这里默认每 5 分钟执行：
 * 先在内存中完成全部业务库读取，再调用 {@link BiSnapshotWriter} 在单个事务中
 * 替换集市事实表。任意一步失败都记录在 {@code bi_etl_run}，旧快照保持不变，
 * 大屏只显示最后成功更新时间，不把异常堆栈暴露给前端。</p>
 */
@Service
public class BiEtlService {

    private static final Logger log = LoggerFactory.getLogger(BiEtlService.class);

    private final BiSnapshotExtractor extractor;
    private final BiSnapshotWriter writer;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BiEtlService(BiSnapshotExtractor extractor,
                        BiSnapshotWriter writer,
                        JdbcTemplate jdbcTemplate) {
        this.extractor = extractor;
        this.writer = writer;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 定时刷新入口。首次启动延迟由配置控制；同一时间只允许一个 ETL，上一轮未结束
     * 时直接跳过本轮，避免重复读库和重复写事实表。
     */
    @Scheduled(fixedDelayString = "${app.bi.refresh-ms:300000}",
            initialDelayString = "${app.bi.initial-delay-ms:15000}")
    public void scheduledRefresh() {
        refresh();
    }

    /**
     * 立即刷新一次。返回 false 表示已有 ETL 正在运行，本次未执行。
     *
     * <p>供管理员手动触发和启动后的首次刷新使用。抽取失败不会抛出到调用方，
     * 接口只返回当前最新成功快照时间。</p>
     */
    public boolean refresh() {
        if (!running.compareAndSet(false, true)) {
            log.info("BI ETL skipped because a previous run is still active");
            return false;
        }
        long started = System.currentTimeMillis();
        Long runId = startRun();
        try {
            log.info("BI ETL started");
            BiSnapshot snapshot = extractor.extract();
            writer.replace(snapshot);
            finishRun(runId, "SUCCESS", snapshot.rowCount(),
                    "rows=" + snapshot.rowCount() + ", durationMs=" + (System.currentTimeMillis() - started));
            log.info("BI ETL succeeded: rows={}, durationMs={}", snapshot.rowCount(),
                    System.currentTimeMillis() - started);
            return true;
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (message.length() > 900) {
                message = message.substring(0, 900);
            }
            finishRun(runId, "FAILED", 0, message);
            log.error("BI ETL failed; previous snapshot remains active", ex);
            return true;
        } finally {
            running.set(false);
        }
    }

    /** 最近一次成功的 ETL 时间；没有成功记录时返回 null。 */
    public LocalDateTime lastSuccessAt() {
        try {
            Timestamp value = jdbcTemplate.queryForObject(
                    "SELECT finished_at FROM bi_etl_run WHERE status = 'SUCCESS' ORDER BY id DESC LIMIT 1",
                    Timestamp.class);
            return value == null ? null : value.toLocalDateTime();
        } catch (Exception ex) {
            return null;
        }
    }

    /** 最近一次 ETL 状态，用于健康检查或运维页面。 */
    public String lastStatus() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT status FROM bi_etl_run ORDER BY id DESC LIMIT 1", String.class);
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    private Long startRun() {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO bi_etl_run(started_at, status, rows_written, message) "
                                + "VALUES (?, 'RUNNING', 0, '')",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return key == null ? null : key.longValue();
        } catch (Exception ex) {
            // 运行记录表不可用时仍然允许 ETL 尝试，最终在日志里保留失败原因。
            log.warn("Unable to create BI ETL run record: {}", ex.getMessage());
            return null;
        }
    }

    private void finishRun(Long runId, String status, long rows, String message) {
        if (runId == null) {
            return;
        }
        try {
            // 时间戳统一由应用写入：平台所有业务表都用应用本地时钟，
            // 数据库 NOW() 走的是数据库服务器时区，两者混用会让“最后同步时间”失真。
            jdbcTemplate.update("UPDATE bi_etl_run SET finished_at = ?, status = ?, rows_written = ?, "
                            + "message = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now()), status, rows, message, runId);
        } catch (Exception ex) {
            log.warn("Unable to update BI ETL run record: {}", ex.getMessage());
        }
    }
}
