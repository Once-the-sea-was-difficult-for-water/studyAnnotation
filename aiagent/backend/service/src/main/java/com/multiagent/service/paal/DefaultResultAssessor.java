package com.multiagent.service.paal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认结果评估器 — 始终通过，后续接入 LLM 驱动的评估逻辑。
 */
@Component
public class DefaultResultAssessor implements ResultAssessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultResultAssessor.class);

    @Override
    public Assessment assess(Object output, String expectation) {
        log.warn("DefaultResultAssessor: auto-passing assessment");
        return new Assessment(true, "默认评估通过");
    }
}
