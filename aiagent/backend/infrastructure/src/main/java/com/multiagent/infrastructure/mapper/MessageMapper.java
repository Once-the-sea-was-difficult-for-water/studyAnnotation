package com.multiagent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.multiagent.infrastructure.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper 接口 — MyBatis-Plus CRUD。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
