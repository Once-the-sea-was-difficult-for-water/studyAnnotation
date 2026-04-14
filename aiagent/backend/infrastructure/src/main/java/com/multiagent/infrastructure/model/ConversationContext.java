package com.multiagent.infrastructure.model;

import com.multiagent.infrastructure.entity.Conversation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话上下文 — 封装当前对话的上下文信息，传递给意图识别器等组件。
 * 该对象在 recognize() 调用过程中不应被修改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    /** 会话 ID */
    private String conversationId;

    /** 关联的对话实体 */
    private Conversation conversation;
}
