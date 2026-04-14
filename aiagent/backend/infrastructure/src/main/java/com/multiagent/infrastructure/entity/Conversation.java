package com.multiagent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话实体 — 用户与系统之间的一次完整交互会话。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversation")
public class Conversation {

    /** 对话 ID */
    @TableId
    private String id;

    /** 用户 ID */
    private String userId;

    /** 对话标题（自动生成或用户自定义） */
    private String title;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 使用过的 Agent 列表（JSON 序列化） */
    private String agentsUsedJson;

    /** 使用过的 Skill 列表（JSON 序列化） */
    private String skillsUsedJson;

    /** 消息列表（非数据库字段，通过关联查询填充） */
    @TableField(exist = false)
    private List<Message> messages;
}
