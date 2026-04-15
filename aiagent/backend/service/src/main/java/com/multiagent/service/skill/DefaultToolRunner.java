package com.multiagent.service.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 默认工具执行器 — 占位实现，后续接入实际工具链。
 */
@Component
public class DefaultToolRunner implements ToolRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolRunner.class);

    @Override
    public Object run(String toolName, Map<String, Object> input) {
        log.warn("DefaultToolRunner: tool '{}' not implemented, returning empty result", toolName);
        return Collections.emptyMap();
    }
}
