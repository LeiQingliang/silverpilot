package com.cecsmsserve.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Small, deterministic retrieval layer over the same curated Markdown package
 * that can be imported into ima. It intentionally returns source identifiers so
 * the model can cite what it actually retrieved.
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final int MAX_DOCUMENTS = 200;
    private static final long MAX_DOCUMENT_BYTES = 1_000_000;

    private final Path root;
    private final Duration cacheTtl;
    private volatile CachedDocuments cache = new CachedDocuments(0, List.of());

    @Autowired
    public KnowledgeBaseService(
            @Value("${agent.knowledge-base-path:../../knowledge-base/ima-ready}") String rootPath,
            @Value("${agent.knowledge-cache-ttl:60s}") Duration cacheTtl) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
        this.cacheTtl = cacheTtl.isNegative() ? Duration.ZERO : cacheTtl;
    }

    KnowledgeBaseService(String rootPath) {
        this(rootPath, Duration.ofMinutes(1));
    }

    public KnowledgeStatus status() {
        List<Document> documents = loadDocuments();
        return new KnowledgeStatus(documents.size(), !documents.isEmpty(),
                documents.stream().map(Document::sourceId).toList());
    }

    public String searchText(String query, int requestedLimit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("知识库检索词不能为空");
        }
        int limit = Math.max(1, Math.min(requestedLimit, 5));
        List<Document> documents = loadDocuments();
        if (documents.isEmpty()) {
            return "知识库尚未加载。请检查 CECSMS_KNOWLEDGE_BASE_PATH 或 ima-ready 知识包。";
        }

        List<String> terms = searchTerms(query);
        List<ScoredDocument> matches = documents.stream()
                .map(document -> new ScoredDocument(document, score(document, query, terms)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredDocument::score).reversed()
                        .thenComparing(item -> item.document().sourceId()))
                .limit(limit)
                .toList();
        if (matches.isEmpty()) {
            return "知识库中没有找到与“" + query.trim() + "”直接相关的已审核内容。请明确问题或转人工核验。";
        }

        StringBuilder result = new StringBuilder("已从受控知识库检索到：\n");
        for (ScoredDocument match : matches) {
            Document document = match.document();
            result.append("- [KB:").append(document.sourceId()).append("] ")
                    .append(document.title()).append("：")
                    .append(excerpt(document.body(), terms)).append('\n');
        }
        result.append("回答时请保留 [KB:...] 来源标识；健康内容只作科普，不替代诊断。 ");
        return result.toString().trim();
    }

    private List<Document> loadDocuments() {
        long now = System.nanoTime();
        CachedDocuments current = cache;
        if (!current.documents().isEmpty()
                && now - current.loadedAtNanos() < cacheTtl.toNanos()) {
            return current.documents();
        }
        synchronized (this) {
            current = cache;
            if (!current.documents().isEmpty()
                    && now - current.loadedAtNanos() < cacheTtl.toNanos()) {
                return current.documents();
            }
            List<Document> loaded = scanDocuments();
            cache = new CachedDocuments(now, loaded);
            return loaded;
        }
    }

    private List<Document> scanDocuments() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root, 2)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase("README.md"))
                    .sorted()
                    .limit(MAX_DOCUMENTS)
                    .map(this::readDocument)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (IOException ex) {
            log.warn("Unable to scan knowledge base at {}: {}", root, ex.getMessage());
            return List.of();
        }
    }

    private Document readDocument(Path path) {
        try {
            if (Files.size(path) > MAX_DOCUMENT_BYTES) {
                log.warn("Skipping oversized knowledge document: {}", path);
                return null;
            }
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            String title = frontmatterValue(raw, "title");
            String status = frontmatterValue(raw, "status");
            if (title == null || !"approved".equalsIgnoreCase(status)) {
                return null;
            }
            String sourceId = path.getFileName().toString().replaceFirst("(?i)\\.md$", "");
            return new Document(sourceId, title, stripFrontmatter(raw));
        } catch (IOException ex) {
            log.warn("Unable to read knowledge document {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private String frontmatterValue(String raw, String key) {
        if (raw == null || !raw.startsWith("---")) {
            return null;
        }
        int end = raw.indexOf("\n---", 3);
        if (end < 0) {
            return null;
        }
        String prefix = raw.substring(3, end);
        for (String line : prefix.split("\\R")) {
            int separator = line.indexOf(':');
            if (separator > 0 && line.substring(0, separator).trim().equalsIgnoreCase(key)) {
                return line.substring(separator + 1).trim().replaceAll("^[\"']|[\"']$", "");
            }
        }
        return null;
    }

    private String stripFrontmatter(String raw) {
        if (!raw.startsWith("---")) {
            return raw;
        }
        int end = raw.indexOf("\n---", 3);
        return end < 0 ? raw : raw.substring(end + 4).trim();
    }

    private List<String> searchTerms(String query) {
        String normalized = normalize(query);
        Set<String> terms = new LinkedHashSet<>();
        for (String word : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 2) terms.add(word);
        }
        String compact = normalized.replaceAll("[^\\p{IsHan}]", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            terms.add(compact.substring(i, Math.min(i + 2, compact.length())));
        }
        if (terms.isEmpty()) terms.add(normalized);
        return new ArrayList<>(terms);
    }

    private int score(Document document, String query, List<String> terms) {
        String haystack = normalize(document.title() + "\n" + document.body());
        int score = haystack.contains(normalize(query)) ? 20 : 0;
        for (String term : terms) {
            if (term.isBlank()) continue;
            int index = haystack.indexOf(term);
            while (index >= 0) {
                score += normalize(document.title()).contains(term) ? 5 : 1;
                index = haystack.indexOf(term, index + term.length());
            }
        }
        return score;
    }

    private String excerpt(String body, List<String> terms) {
        String plain = body
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^[-*]\\s+", "")
                .replaceAll("`+", "")
                .replaceAll("\\s+", " ")
                .trim();
        int start = 0;
        for (String term : terms) {
            int found = normalize(plain).indexOf(term);
            if (found >= 0) {
                start = Math.max(0, found - 45);
                break;
            }
        }
        int end = Math.min(plain.length(), start + 240);
        String value = plain.substring(start, end).trim();
        return (start > 0 ? "…" : "") + value + (end < plain.length() ? "…" : "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private record Document(String sourceId, String title, String body) { }
    private record ScoredDocument(Document document, int score) { }
    private record CachedDocuments(long loadedAtNanos, List<Document> documents) { }

    /** Public runtime status deliberately omits the server's absolute filesystem path. */
    public record KnowledgeStatus(int approvedDocuments, boolean ready, List<String> sources) { }
}
