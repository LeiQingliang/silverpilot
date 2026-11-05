package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseServiceTests {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), BUSINESS_ZONE);

    @TempDir
    Path tempDir;

    @Test
    void retrievesOnlyApprovedDocumentsWithSourceId() throws Exception {
        Files.writeString(tempDir.resolve("safe.md"), """
                ---
                title: 写操作安全策略
                status: approved
                reviewed_at: 2026-08-18
                next_review_at: 2026-11-18
                ---
                所有预约必须在用户确认后才执行，确认前不得修改业务数据。
                """);
        Files.writeString(tempDir.resolve("draft.md"), """
                ---
                title: 未审核草稿
                status: draft
                ---
                这是不应被检索的内容。
                """);
        KnowledgeBaseService service = new KnowledgeBaseService(tempDir.toString(), Duration.ofMinutes(1), TEST_CLOCK);

        String result = service.searchText("预约确认安全", 3);

        assertTrue(result.contains("[KB:safe]"));
        assertTrue(result.contains("确认"));
        assertFalse(result.contains("未审核草稿"));
        assertTrue(service.status().ready());
        assertEquals(1, service.status().approvedDocuments());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "2026-08-18,2026-11-06,true",
            "2026-11-06,2026-11-06,true",
            "2026-08-18,2026-11-05,false",
            "2026-11-07,2026-11-18,false",
            "2026-11-06,2026-11-05,false",
            "MISSING,2026-11-18,false",
            "2026-08-18,MISSING,false",
            "2026-02-30,2026-11-18,false",
            "2026-08-18,2026-13-01,false",
            "2026-8-18,2026-11-18,false",
            "2026-08-18,not-a-date,false",
            "0000-01-01,2026-11-18,false"
    }, nullValues = "MISSING")
    void onlyIndexesApprovedDocumentsWithinValidReviewWindow(
            String reviewedAt, String nextReviewAt, boolean eligible) throws Exception {
        writeDocument("dated.md", "approved", reviewedAt, nextReviewAt);
        KnowledgeBaseService service = new KnowledgeBaseService(tempDir.toString(), Duration.ofMinutes(1), TEST_CLOCK);

        assertEquals(eligible, service.searchText("预约确认", 3).contains("[KB:dated]"));
        assertEquals(eligible ? 1 : 0, service.status().approvedDocuments());
        assertEquals(eligible, service.status().ready());
    }

    @ParameterizedTest
    @CsvSource({"draft", "governance"})
    void neverIndexesNonApprovedDocumentsEvenWithValidDates(String status) throws Exception {
        writeDocument("excluded.md", status, "2026-08-18", "2026-11-18");
        KnowledgeBaseService service = new KnowledgeBaseService(tempDir.toString(), Duration.ofMinutes(1), TEST_CLOCK);
        assertFalse(service.status().ready());
        assertFalse(service.searchText("预约确认", 3).contains("[KB:excluded]"));
    }

    @Test
    void cachedDocumentExpiresAtShanghaiMidnightBeforeCacheTtl() throws Exception {
        writeDocument("expires.md", "approved", "2026-08-18", "2026-11-06");
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T15:59:59Z"));
        KnowledgeBaseService service = new KnowledgeBaseService(tempDir.toString(), Duration.ofHours(24), clock);
        assertTrue(service.searchText("预约确认", 3).contains("[KB:expires]"));

        clock.instant = Instant.parse("2026-11-06T16:00:00Z");

        assertEquals(0, service.status().approvedDocuments());
        assertFalse(service.searchText("预约确认", 3).contains("[KB:expires]"));
    }

    @Test
    void duplicateReviewMetadataCannotApproveAnExpiredDocument() throws Exception {
        writeDocument("duplicate.md", "approved", "2026-08-18", "2026-10-18\nnext_review_at: 2026-12-18");
        KnowledgeBaseService service = new KnowledgeBaseService(tempDir.toString(), Duration.ofMinutes(1), TEST_CLOCK);
        assertFalse(service.status().ready());
    }

    @Test
    void dateFieldsInBodyCannotReplaceMissingFrontmatter() throws Exception {
        Files.writeString(tempDir.resolve("spoof.md"), """
                ---
                title: 预约确认
                status: approved
                ---
                reviewed_at: 2026-08-18
                next_review_at: 2026-12-18
                """);
        KnowledgeBaseService service = new KnowledgeBaseService(tempDir.toString(), Duration.ofMinutes(1), TEST_CLOCK);
        assertFalse(service.status().ready());
    }

    private void writeDocument(String name, String status, String reviewedAt, String nextReviewAt) throws Exception {
        Files.writeString(tempDir.resolve(name), "---\ntitle: 预约确认安全\nstatus: " + status + "\n"
                + (reviewedAt == null ? "" : "reviewed_at: " + reviewedAt + "\n")
                + (nextReviewAt == null ? "" : "next_review_at: " + nextReviewAt + "\n")
                + "---\n预约确认后才可执行。\n");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return BUSINESS_ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
