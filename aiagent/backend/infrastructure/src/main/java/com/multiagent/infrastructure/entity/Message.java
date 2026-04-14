package com.multiagent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.multiagent.infrastructure.model.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体 — 对话中的单条交互记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("message")
public class Message {

    /** 消息 ID */
    @TableId
    private String id;

    /** 所属对话 ID */
    private String conversationId;

    /** 消息角色 */
    private MessageRole role;

    /** 消息内容 */
    private String content;

    /** 消息时间戳 */
    private LocalDateTime timestamp;

    /** 处理该消息的 Agent ID */
    private String agentId;

    /** 触发的 Skill ID */
    private String skillId;

    /** 全链路追踪 ID */
    private String traceId;

    /** 附件信息（图表、表格等富文本结果，JSON 序列化） */
    private String attachmentsJson;
}
