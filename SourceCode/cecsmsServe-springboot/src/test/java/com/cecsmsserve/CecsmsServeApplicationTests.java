package com.cecsmsserve;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CecsmsServeApplicationTests {

    private static final String HOST_CONFIG_IMPORTS =
            "optional:file:./config/application-host.properties,"
                    + "optional:file:./SourceCode/cecsmsServe-springboot/config/application-host.properties";

    @Test
    void contextLoads() throws IOException {
        List<PropertySource<?>> documents = new PropertiesPropertySourceLoader().load(
                "application", new ClassPathResource("application.properties"));

        assertTrue(documents.stream().anyMatch(document ->
                        "host".equals(document.getProperty("spring.config.activate.on-profile"))
                                && HOST_CONFIG_IMPORTS.equals(document.getProperty("spring.config.import"))),
                "The host profile must load generated configuration from both supported IDEA working directories");
    }
}
