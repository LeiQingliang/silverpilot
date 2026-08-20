package com.cecsmsserve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "file.asset-base-path=src/test/resources/asset-fixtures",
        "file.legacy-asset-base-path=src/test/resources/legacy-asset-fixtures"
})
class StaticAssetFallbackIntegrationTests {

    @LocalServerPort
    private int port;

    private final Path writableAsset = Path.of(
            System.getProperty("java.io.tmpdir"), "cecsms-test-uploads", "image", "fallback.txt");

    @BeforeEach
    @AfterEach
    void removeWritableFixture() throws Exception {
        Files.deleteIfExists(writableAsset);
    }

    @Test
    void servesReadOnlyAssetWhenWritableUploadDirectoryDoesNotContainIt() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/image/fallback.txt"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("fallback-asset-ok"));
    }

    @Test
    void writableUploadTakesPrecedenceOverReadOnlyAsset() throws Exception {
        Files.createDirectories(writableAsset.getParent());
        Files.writeString(writableAsset, "writable-upload-wins", StandardCharsets.UTF_8);

        HttpResponse<String> response = getAsset();

        assertEquals(200, response.statusCode());
        assertEquals("writable-upload-wins", response.body());
    }

    @Test
    void servesRepositoryMediaFromTheSecondReadOnlyRoot() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/image/repository-media.txt"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("repository-media-ok", response.body().trim());
    }

    private HttpResponse<String> getAsset() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/image/fallback.txt"))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
