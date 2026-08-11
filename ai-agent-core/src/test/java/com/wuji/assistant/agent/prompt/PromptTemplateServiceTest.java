package com.wuji.assistant.agent.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * PromptTemplateService 单元测试。
 *
 * @author liudy
 */
class PromptTemplateServiceTest {

    @Test
    void renderReplacesVariables() {
        PromptTemplateService service = new PromptTemplateService(null);
        String out = service.render("你好，{{ message }}，来自 {{user}}", Map.of(
                "message", "世界",
                "user", "张三"
        ));
        assertEquals("你好，世界，来自 张三", out);
    }

    @Test
    void renderMissingVarAsEmpty() {
        PromptTemplateService service = new PromptTemplateService(null);
        assertEquals("X-", service.render("X-{{missing}}", Map.of()));
    }

    @Test
    void invalidatePublishesChangedEvent() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PromptTemplateService service = new PromptTemplateService(null, publisher);
        service.invalidate("rag.answer.system");
        verify(publisher).publishEvent(new PromptTemplateChangedEvent("rag.answer.system"));
    }
}
