package com.cecsmsserve.util.upload;

import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadUtilsTests {

    @TempDir
    Path uploadRoot;

    @Test
    void storesAnAllowedExtensionUnderTheConfiguredRoot() throws IOException {
        byte[] content = "synthetic upload".getBytes();
        MockMultipartFile upload = new MockMultipartFile(
                "file", "../../notes.TXT", "text/plain", content);

        CommonResult<String> result = FileUploadUtils.upload(upload, uploadRoot.toString(), "file");

        assertEquals(200, result.getCode());
        assertNotNull(result.getResult());
        assertTrue(result.getResult().matches("/file/\\d{8}/[a-f0-9]{32}\\.txt"));
        Path storedFile = uploadRoot.resolve(result.getResult().substring(1)).normalize();
        assertTrue(storedFile.startsWith(uploadRoot.toAbsolutePath().normalize()));
        assertArrayEquals(content, Files.readAllBytes(storedFile));
    }

    @Test
    void rejectsExtensionsOutsideTheExplicitAllowList() throws IOException {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "payload.sh", "text/plain", "echo unsafe".getBytes());

        CommonResult<String> result = FileUploadUtils.upload(upload, uploadRoot.toString(), "file");

        assertEquals(400, result.getCode());
        assertEquals("不支持该文件格式", result.getMsg());
        try (var paths = Files.walk(uploadRoot)) {
            assertEquals(1, paths.count());
        }
    }
}
