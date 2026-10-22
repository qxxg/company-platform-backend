package com.company.platform.ai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * AI 对话审计仓储。
 *
 * <p>审计写入失败不能影响用户拿到回答，因此这里只记日志、不抛异常。问题、
 * 回答和工具摘要全部落库，便于后续追溯“模型当时看到了什么、答了什么”。
 * 生产环境还应按公司数据保留策略定期归档，避免问题文本无限增长。</p>
 */
@Repository
public class AiChatLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AiChatLogRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public AiChatLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 写入一条对话审计记录。 */
    public void save(AiChatLogEntry entry) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO ai_chat_log
                                (user_id, username, session_id, question, answer, model,
                                 tool_trace, duration_ms, status, error_message)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    entry.userId(),
                    truncate(entry.username(), 64),
                    truncate(entry.sessionId(), 64),
                    truncate(entry.question(), 4000),
                    truncate(entry.answer(), 16000),
                    truncate(entry.model(), 128),
                    truncate(entry.toolTrace(), 16000),
                    entry.durationMs(),
                    truncate(entry.status(), 16),
                    truncate(entry.errorMessage(), 500));
        } catch (Exception ex) {
            log.warn("Failed to write AI chat audit log, sessionId={}", entry.sessionId(), ex);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
