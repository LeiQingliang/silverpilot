package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void retrievesOnlyApprovedDocumentsWithSourceId() throws Exception {
        Files.writeString(tempDir.resolve("safe.md"), """
                ---
                title: 写操作安全策略
                status: approved
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
        KnowledgeBaseService service = new KnowledgeBaseService(tempDir.toString());

        String result = service.searchText("预约确认安全", 3);

        assertTrue(result.contains("[KB:safe]"));
        assertTrue(result.contains("确认"));
        assertFalse(result.contains("未审核草稿"));
        assertTrue(service.status().ready());
        assertTrue(service.status().approvedDocuments() == 1);
    }
}
