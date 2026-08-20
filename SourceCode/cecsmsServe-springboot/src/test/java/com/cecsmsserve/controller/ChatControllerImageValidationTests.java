package com.cecsmsserve.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ChatControllerImageValidationTests {

    private final ChatController controller = mock(ChatController.class,
            invocation -> invocation.callRealMethod());

    @Test
    void validatesSupportedImageFileSignatures() {
        assertTrue(controller.hasValidImageSignature("image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
        assertTrue(controller.hasValidImageSignature("image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
        assertTrue(controller.hasValidImageSignature("image/webp",
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
        assertFalse(controller.hasValidImageSignature("image/png", "not-an-image".getBytes()));
        assertFalse(controller.hasValidImageSignature("image/gif", new byte[] {'G', 'I', 'F'}));
    }
}
