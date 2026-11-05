package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiProviderServiceTests {

    @Test
    void longCatIsExplicitTextProviderAndDoesNotReplaceExistingAutoPriority() {
        DeepSeekClient deepseek = mock(DeepSeekClient.class);
        DoubaoClient doubao = mock(DoubaoClient.class);
        LongCatClient longcat = mock(LongCatClient.class);
        when(longcat.model()).thenReturn("LongCat-2.5");
        when(longcat.isConfigured()).thenReturn(true);
        AiProviderService service = new AiProviderService(deepseek, doubao, longcat,
                mock(MockAiProviderClient.class), "deepseek-model", false, true, 1800, 12000);

        assertEquals("longcat", service.resolve("longcat", false).id());
        assertEquals("longcat", service.resolve("auto", false).id());
        assertFalse(service.resolve("longcat", false).vision());
        assertThrows(IllegalArgumentException.class, () -> service.resolve("longcat", true));
        when(doubao.isConfigured()).thenReturn(true);
        assertEquals("doubao", service.resolve("auto", false).id());
        when(deepseek.isConfigured()).thenReturn(true);
        assertEquals("deepseek", service.resolve("auto", false).id());
        when(longcat.isConfigured()).thenReturn(false);
        assertThrows(AiProviderException.class, () -> service.resolve("longcat", false));
    }

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
