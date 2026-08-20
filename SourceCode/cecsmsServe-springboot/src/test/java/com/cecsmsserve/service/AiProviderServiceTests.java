package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiProviderServiceTests {

    @Test
    void autoUsesDeepSeekForTextAndDoubaoForVision() {
        DeepSeekClient deepSeek = mock(DeepSeekClient.class);
        DoubaoClient doubao = mock(DoubaoClient.class);
        when(deepSeek.isConfigured()).thenReturn(true);
        when(doubao.isConfigured()).thenReturn(true);
        when(doubao.model()).thenReturn("doubao-vision-endpoint");
        AiProviderService service = new AiProviderService(deepSeek, doubao, "deepseek-model", false);

        assertEquals("deepseek", service.resolve("auto", false).id());
        assertEquals("doubao", service.resolve("auto", true).id());
    }

    @Test
    void visionCannotBeSentToTextOnlyChannel() {
        DeepSeekClient deepSeek = mock(DeepSeekClient.class);
        DoubaoClient doubao = mock(DoubaoClient.class);
        when(deepSeek.isConfigured()).thenReturn(true);
        AiProviderService service = new AiProviderService(deepSeek, doubao, "deepseek-model", false);

        assertThrows(IllegalArgumentException.class, () -> service.resolve("deepseek", true));
        assertThrows(AiProviderException.class, () -> service.resolve("auto", true));
    }
}
