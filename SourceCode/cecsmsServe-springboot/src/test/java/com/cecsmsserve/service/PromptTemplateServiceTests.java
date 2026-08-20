package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateServiceTests {

    @Test
    void loadsVersionedPromptsAndResolvesBusinessDate() {
        PromptTemplateService service = new PromptTemplateService(
                new DefaultResourceLoader(),
                "classpath:prompts/silverpilot-agent-v3.md",
                "classpath:prompts/care-recommendation-v1.md");

        String prompt = service.renderAgent(LocalDate.of(2026, 8, 18));

        assertTrue(prompt.contains("2026-08-18"));
        assertTrue(prompt.contains("写工具只创建待确认动作"));
        assertFalse(prompt.contains("{{businessDate}}"));
        assertTrue(prompt.contains("模糊指令处理"));
        assertTrue(prompt.contains("第一行直接给结果"));
        assertEquals("3.0.0", service.metadata(PromptTemplateService.AGENT).version());
        assertEquals(16, service.metadata(PromptTemplateService.AGENT).sha256().length());
    }
}
