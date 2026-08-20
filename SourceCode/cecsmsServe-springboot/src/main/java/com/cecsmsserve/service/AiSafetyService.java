package com.cecsmsserve.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AiSafetyService {

    private static final Pattern MODEL_KEY = Pattern.compile(
            "(?i)(?:sk|ak)[-_][A-Za-z0-9_-]{16,}");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern ASSIGNMENT_SECRET = Pattern.compile(
            "(?i)(?:api[_ -]?key|access[_ -]?token|client[_ -]?secret|password)\\s*[:=]\\s*[^\\s]{12,}");
    private static final Pattern ID_NUMBER = Pattern.compile(
            "(?<!\\d)([1-9]\\d{5})(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9])\\d{7}(\\d{2})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])([A-Z0-9._%+-]{1,3})[A-Z0-9._%+-]*@([A-Z0-9.-]+\\.[A-Z]{2,})(?![A-Z0-9._%+-])");

    public void assertNoSecrets(List<String> contents) {
        for (String content : contents) {
            if (content == null) continue;
            if (MODEL_KEY.matcher(content).find()
                    || PRIVATE_KEY.matcher(content).find()
                    || ASSIGNMENT_SECRET.matcher(content).find()) {
                throw new IllegalArgumentException("检测到疑似密钥、令牌或密码，已阻止发送。请先删除敏感凭据再重试");
            }
        }
    }

    /** Redacts identifiers that are not required by any current business tool. */
    public String redactForModel(String value) {
        if (value == null || value.isBlank()) return value;
        String redacted = ID_NUMBER.matcher(value).replaceAll("$1************");
        redacted = PHONE.matcher(redacted).replaceAll("$1*******$2");
        return EMAIL.matcher(redacted).replaceAll("$1***@$2");
    }
}
