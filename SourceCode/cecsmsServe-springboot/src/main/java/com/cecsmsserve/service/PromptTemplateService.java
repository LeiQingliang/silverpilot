package com.cecsmsserve.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PromptTemplateService {

    public static final String AGENT = "agent";
    public static final String CARE_RECOMMENDATION = "care-recommendation";

    private final Map<String, PromptTemplate> templates;

    public PromptTemplateService(
            ResourceLoader resourceLoader,
            @Value("${agent.prompt.agent-location:classpath:prompts/silverpilot-agent-v3.md}") String agentLocation,
            @Value("${agent.prompt.care-recommendation-location:classpath:prompts/care-recommendation-v1.md}") String recommendationLocation) {
        Map<String, PromptTemplate> loaded = new LinkedHashMap<>();
        loaded.put(AGENT, load(resourceLoader.getResource(agentLocation)));
        loaded.put(CARE_RECOMMENDATION, load(resourceLoader.getResource(recommendationLocation)));
        this.templates = Map.copyOf(loaded);
    }

    public String renderAgent(LocalDate businessDate) {
        return render(AGENT, Map.of("businessDate", businessDate.toString()));
    }

    public String renderCareRecommendation(LocalDate businessDate) {
        return render(CARE_RECOMMENDATION, Map.of("businessDate", businessDate.toString()));
    }

    public PromptMetadata metadata(String key) {
        PromptTemplate template = requireTemplate(key);
        return new PromptMetadata(template.id(), template.version(), template.sha256());
    }

    public Map<String, PromptMetadata> metadata() {
        Map<String, PromptMetadata> result = new LinkedHashMap<>();
        templates.forEach((key, value) -> result.put(
                key, new PromptMetadata(value.id(), value.version(), value.sha256())));
        return Map.copyOf(result);
    }

    private String render(String key, Map<String, String> variables) {
        String rendered = requireTemplate(key).body();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        if (rendered.matches("(?s).*\\{\\{[A-Za-z0-9_-]+}}.*")) {
            throw new IllegalStateException("Prompt template contains an unresolved variable: " + key);
        }
        return rendered;
    }

    private PromptTemplate requireTemplate(String key) {
        PromptTemplate template = templates.get(key);
        if (template == null) throw new IllegalArgumentException("Unknown prompt template: " + key);
        return template;
    }

    private PromptTemplate load(Resource resource) {
        try {
            String raw = resource.getContentAsString(StandardCharsets.UTF_8);
            if (!raw.startsWith("---")) {
                throw new IllegalStateException("Prompt metadata is missing: " + resource.getDescription());
            }
            int end = raw.indexOf("\n---", 3);
            if (end < 0) throw new IllegalStateException("Prompt metadata is invalid: " + resource.getDescription());
            String metadata = raw.substring(3, end);
            String id = metadataValue(metadata, "id");
            String version = metadataValue(metadata, "version");
            String body = raw.substring(end + 4).trim();
            if (id.isBlank() || version.isBlank() || body.isBlank()) {
                throw new IllegalStateException("Prompt id, version and body are required: " + resource.getDescription());
            }
            return new PromptTemplate(id, version, sha256(raw), body);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load prompt template: " + resource.getDescription(), ex);
        }
    }

    private String metadataValue(String metadata, String name) {
        for (String line : metadata.split("\\R")) {
            int separator = line.indexOf(':');
            if (separator > 0 && line.substring(0, separator).trim().equalsIgnoreCase(name)) {
                return line.substring(separator + 1).trim();
            }
        }
        return "";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fingerprint prompt", ex);
        }
    }

    private record PromptTemplate(String id, String version, String sha256, String body) { }
    public record PromptMetadata(String id, String version, String sha256) { }
}
