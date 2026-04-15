package com.multiagent.service.fallback;

import com.multiagent.infrastructure.model.UnifiedResponse;
import org.springframework.stereotype.Component;

/**
 * 默认质量门禁 — 检查响应非空且内容不为空。
 */
@Component
public class DefaultQualityGate implements QualityGate {

    @Override
    public boolean check(UnifiedResponse response) {
        if (response == null) {
            return false;
        }
        String content = response.getContent();
        return content != null && !content.isBlank();
    }
}
