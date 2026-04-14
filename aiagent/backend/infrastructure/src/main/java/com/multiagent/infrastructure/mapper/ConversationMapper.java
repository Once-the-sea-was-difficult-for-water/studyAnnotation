package com.multiagent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.multiagent.infrastructure.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话 Mapper 接口 — MyBatis-Plus CRUD。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
