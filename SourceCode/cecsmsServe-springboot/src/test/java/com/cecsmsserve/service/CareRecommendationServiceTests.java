package com.cecsmsserve.service;

import com.cecsmsserve.entity.ServiceType;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareRecommendationServiceTests {

    @Test
    void validatesStructuredOutputAgainstRealServiceIds() {
        IServiceTypeService serviceTypes = mock(IServiceTypeService.class);
        ServiceType service = new ServiceType();
        service.setId(9);
        service.setServiceName("上门助浴");
        service.setLeaderId(2);
        service.setState(1);
        when(serviceTypes.list(org.mockito.ArgumentMatchers.<Wrapper<ServiceType>>any()))
                .thenReturn(List.of(service));

        KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
        when(knowledge.searchText(anyString(), anyInt())).thenReturn("已从受控知识库检索到：\n- [KB:safe] 助浴安全");
        AiProviderService providers = mock(AiProviderService.class);
        AiProviderService.ProviderSelection selection = new AiProviderService.ProviderSelection(
                "deepseek", "DeepSeek", "model", false, false);
        when(providers.resolve("auto", false)).thenReturn(selection);
        when(providers.decorateStructuredBody(any(), any(), anyInt())).thenReturn(Map.of());
        Map<String, Object> response = Map.of("response", "ok");
        when(providers.chatCompletion(any(), any())).thenReturn(response);
        when(providers.firstText(response)).thenReturn("""
                {"summary":"行动不便，需要洗浴支持","urgency":"medium","recommendations":[
                  {"serviceId":9,"serviceName":"任意模型名称","reason":"行动不便","nextStep":"核对禁忌","evidence":["KB:safe"]},
                  {"serviceId":999,"serviceName":"虚构服务","reason":"错误","nextStep":"错误","evidence":[]}
                ],"safetyNotice":"先由工作人员评估","disclaimer":"不替代医疗诊断"}
                """);
        when(providers.usage(response)).thenReturn(new AiProviderService.TokenUsage(30, 20, 50));

        PromptTemplateService prompts = new PromptTemplateService(
                new DefaultResourceLoader(),
                "classpath:prompts/silverpilot-agent-v3.md",
                "classpath:prompts/care-recommendation-v1.md");
        AgentRunService runs = mock(AgentRunService.class);
        CareRecommendationService recommendationService = new CareRecommendationService(
                serviceTypes, knowledge, prompts, providers, new AiSafetyService(), runs,
                JsonMapper.builder().build());

        CareRecommendationService.CarePlan plan = recommendationService.create(
                17, "老人行动不便，需要安全洗浴支持", "auto");

        assertEquals(1, plan.recommendations().size());
        assertEquals(9, plan.recommendations().getFirst().serviceId());
        assertEquals("上门助浴", plan.recommendations().getFirst().serviceName());
        assertEquals(50, plan.totalTokens());
        assertTrue(plan.recommendations().getFirst().evidence().contains("BUSINESS:9"));
        verify(runs).record(any(AgentRunService.RunRecord.class));
    }
}
