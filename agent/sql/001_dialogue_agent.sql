CREATE TABLE IF NOT EXISTS biz_agent_session (
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '会话创建用户ID',
    project_id BIGINT NULL COMMENT '预绑定项目ID',
    title VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    created_at DATETIME NOT NULL COMMENT '创建时间（Asia/Shanghai）',
    updated_at DATETIME NOT NULL COMMENT '最后活动时间（Asia/Shanghai）',
    PRIMARY KEY (session_id),
    KEY idx_agent_session_user_updated (user_id, updated_at),
    KEY idx_agent_session_project_updated (project_id, updated_at),
    CONSTRAINT fk_agent_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体会话表';

CREATE TABLE IF NOT EXISTS biz_agent_message (
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '消息所属用户ID',
    role VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL COMMENT '消息正文',
    status VARCHAR(16) NOT NULL COMMENT 'generating/completed/failed',
    project_id BIGINT NULL COMMENT '本轮关联项目ID',
    intent_type VARCHAR(20) NULL COMMENT '智能体意图类型',
    context_json JSON NULL COMMENT '页面上下文',
    response_duration_ms INT NULL COMMENT '响应耗时毫秒',
    error_message VARCHAR(255) NULL COMMENT '失败原因',
    created_at DATETIME NOT NULL COMMENT '创建时间（Asia/Shanghai）',
    completed_at DATETIME NULL COMMENT '完成时间（Asia/Shanghai）',
    PRIMARY KEY (message_id),
    KEY idx_agent_message_session_created (session_id, created_at),
    KEY idx_agent_message_session_status (session_id, status),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES biz_agent_session(session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体消息与SSE运行状态表';
