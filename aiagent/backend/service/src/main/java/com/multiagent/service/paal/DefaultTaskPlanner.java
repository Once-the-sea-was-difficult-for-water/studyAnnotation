package com.multiagent.service.paal;

import com.multiagent.infrastructure.model.SharedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 默认任务规划器 — 占位实现，后续接入 LLM 驱动的任务分解。
 */
@Component
public class DefaultTaskPlanner implements TaskPlanner {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskPlanner.class);

    @Override
    public Object plan(PAALInput input, SharedContext context) {
        log.warn("DefaultTaskPlanner: returning empty plan for task '{}'", input.getDescription());
        return Collections.singletonMap("steps", Collections.emptyList());
    }
}
