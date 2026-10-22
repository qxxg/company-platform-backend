-- AI 助手：对话审计与权限。
-- 本脚本只新增审计表和权限，不创建业务数据表；AI 读取业务数据时仍调用原服务接口。
USE company_platform;

CREATE TABLE IF NOT EXISTS ai_chat_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL COMMENT '用户 ID；机器令牌可为空',
    username VARCHAR(64) NOT NULL COMMENT '登录名',
    session_id VARCHAR(64) NOT NULL COMMENT '前端会话标识',
    question VARCHAR(4000) NOT NULL COMMENT '用户问题',
    answer MEDIUMTEXT DEFAULT NULL COMMENT '模型回答',
    model VARCHAR(128) DEFAULT NULL COMMENT '模型标签',
    tool_trace MEDIUMTEXT DEFAULT NULL COMMENT '工具调用轨迹 JSON，含结果摘要',
    duration_ms INT NOT NULL DEFAULT 0 COMMENT '服务端耗时毫秒',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_chat_user_time (user_id, created_at),
    KEY idx_ai_chat_session (session_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 助手对话审计';

-- 所有员工角色都可以使用只读助手；数据可见范围仍由目标业务接口决定。
INSERT IGNORE INTO sys_permission (code, name, type, parent_id, status)
VALUES ('ai:assistant:use', '使用 AI 助手', 'api', 0, 1);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.code = 'ai:assistant:use'
WHERE r.code IN ('ADMIN', 'SALES', 'FACTORY_MANAGER', 'OPERATOR', 'FINANCE');
