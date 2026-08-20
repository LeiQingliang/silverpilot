package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSafetyServiceTests {

    private final AiSafetyService service = new AiSafetyService();

    @Test
    void blocksCredentialsBeforeTheyReachAProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> service.assertNoSecrets(List.of("api_key=" + "sk-" + "example-1234567890abcdef")));
        assertThrows(IllegalArgumentException.class,
                () -> service.assertNoSecrets(List.of("-----BEGIN " + "PRIVATE KEY-----")));
    }

    @Test
    void redactsPhoneEmailAndIdentityNumberButKeepsIntent() {
        String phone = "138" + "1234" + "5678";
        String identityNumber = "620102" + "19500101" + "1234";
        String value = service.redactForModel(
                "联系" + phone + "，邮箱abc@example.com，身份证" + identityNumber + "，需要助餐");

        assertFalse(value.contains(phone));
        assertFalse(value.contains("abc@example.com"));
        assertFalse(value.contains(identityNumber));
        assertTrue(value.contains("需要助餐"));
    }
}
