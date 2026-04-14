-- Conversation table
CREATE TABLE IF NOT EXISTS conversation (
    id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    title         VARCHAR(256) NOT NULL DEFAULT '',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    agents_used_json TEXT,
    skills_used_json TEXT,
    INDEX idx_conversation_user_id (user_id),
    INDEX idx_conversation_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Message table
CREATE TABLE IF NOT EXISTS message (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    conversation_id  VARCHAR(64)  NOT NULL,
    role             VARCHAR(16)  NOT NULL,
    content          LONGTEXT,
    timestamp        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    agent_id         VARCHAR(64),
    skill_id         VARCHAR(64),
    trace_id         VARCHAR(64),
    attachments_json LONGTEXT,
    INDEX idx_message_conversation_id (conversation_id),
    INDEX idx_message_timestamp (timestamp),
    INDEX idx_message_trace_id (trace_id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
