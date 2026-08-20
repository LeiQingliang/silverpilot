package com.cecsmsserve.util;

import com.cecsmsserve.util.upload.FileUploadInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Restores historical demo placeholders to the repository images and refuses
 * to report the application ready while a persisted image URL is unresolved.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "app.media-integrity.enabled", havingValue = "true", matchIfMissing = true)
public class PersistedImageIntegrityMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersistedImageIntegrityMigration.class);
    private static final Pattern IMAGE_URL = Pattern.compile(
            "^/image/[A-Za-z0-9._/-]+\\.(?:png|jpe?g|gif|webp)$",
            Pattern.CASE_INSENSITIVE);
    private static final String IMAGE_QUERY = """
            SELECT `image` FROM `activity` WHERE `image` IS NOT NULL AND `image` <> ''
            UNION
            SELECT `avatar` FROM `news` WHERE `avatar` IS NOT NULL AND `avatar` <> ''
            UNION
            SELECT `image_url` FROM `recipe` WHERE `image_url` IS NOT NULL AND `image_url` <> ''
            UNION
            SELECT `image` FROM `service_type` WHERE `image` IS NOT NULL AND `image` <> ''
            """;

    private final JdbcTemplate jdbcTemplate;
    private final List<Path> mediaRoots;

    public PersistedImageIntegrityMigration(DataSource dataSource, FileUploadInfo fileUploadInfo) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.mediaRoots = configuredRoots(fileUploadInfo);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int migrated = restoreHistoricalPaths();
        int removedMissingLegacyPaths = jdbcTemplate.update(
                "UPDATE `recipe` SET `image_url` = NULL WHERE `image_url` = ?",
                "/image/recipe/fish.jpg");
        removedMissingLegacyPaths += jdbcTemplate.update(
                "UPDATE `recipe` SET `image_url` = NULL WHERE `image_url` = ?",
                "/image/demo/meal.svg");

        List<String> persistedPaths = jdbcTemplate.queryForList(IMAGE_QUERY, String.class);
        if (persistedPaths.isEmpty()) {
            throw new IllegalStateException("Database exposes no persisted images for the runtime media contract");
        }
        Set<String> uniquePaths = new LinkedHashSet<>(persistedPaths);
        for (String imageUrl : uniquePaths) {
            resolveRealImage(imageUrl);
        }

        if (migrated > 0 || removedMissingLegacyPaths > 0) {
            log.info("Restored {} historical image paths and cleared {} undistributed legacy paths",
                    migrated, removedMissingLegacyPaths);
        }
        log.info("Validated {} persisted image URLs against configured runtime media roots", uniquePaths.size());
    }

    private int restoreHistoricalPaths() {
        int migrated = 0;
        for (Map.Entry<Integer, String> entry : activityImages().entrySet()) {
            migrated += restore("activity", "image", entry.getKey(),
                    "/image/demo/activity.svg", entry.getValue());
        }
        for (Map.Entry<Integer, String> entry : newsImages().entrySet()) {
            migrated += restore("news", "avatar", entry.getKey(),
                    "/image/demo/news.svg", entry.getValue());
        }
        for (Map.Entry<Integer, String> entry : recipeImages().entrySet()) {
            migrated += restore("recipe", "image_url", entry.getKey(),
                    "/image/demo/meal.svg", entry.getValue());
        }
        for (int id = 1; id <= 27; id++) {
            migrated += restore("service_type", "image", id,
                    legacyServiceImage(id), "/image/20260327/" + id + ".png");
        }
        return migrated;
    }

    private int restore(String table, String column, int id, String legacyUrl, String targetUrl) {
        List<String> currentValues = jdbcTemplate.queryForList(
                "SELECT `" + column + "` FROM `" + table + "` WHERE `id` = ?", String.class, id);
        if (currentValues.size() != 1 || !legacyUrl.equals(currentValues.get(0))) return 0;
        resolveRealImage(targetUrl);
        return jdbcTemplate.update(
                "UPDATE `" + table + "` SET `" + column + "` = ? WHERE `id` = ? AND `" + column + "` = ?",
                targetUrl, id, legacyUrl);
    }

    private Path resolveRealImage(String imageUrl) {
        if (imageUrl == null || imageUrl.contains("..") || !IMAGE_URL.matcher(imageUrl).matches()) {
            throw new IllegalStateException("Persisted image URL is not a safe local raster path: " + imageUrl);
        }
        Path relativePath = Path.of(imageUrl.substring(1)).normalize();
        if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
            throw new IllegalStateException("Persisted image URL escaped its configured media root: " + imageUrl);
        }
        for (Path root : mediaRoots) {
            Path candidate = root.resolve(relativePath).normalize();
            if (candidate.startsWith(root) && Files.isRegularFile(candidate) && hasMatchingImageSignature(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Persisted image URL has no real matching file: " + imageUrl);
    }

    private boolean hasMatchingImageSignature(Path path) {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = Files.newInputStream(path)) {
            read = input.read(header);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read persisted image file: " + path, ex);
        }
        if (read < 3) return false;
        String extension = extension(path);
        return switch (extension) {
            case "png" -> read >= 8
                    && unsigned(header[0]) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
                    && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a;
            case "jpg", "jpeg" -> unsigned(header[0]) == 0xff
                    && unsigned(header[1]) == 0xd8 && unsigned(header[2]) == 0xff;
            case "gif" -> read >= 6 && (ascii(header, 0, 6).equals("GIF87a") || ascii(header, 0, 6).equals("GIF89a"));
            case "webp" -> read >= 12 && ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 4).equals("WEBP");
            default -> false;
        };
    }

    private static List<Path> configuredRoots(FileUploadInfo fileUploadInfo) {
        Set<Path> roots = new LinkedHashSet<>();
        addRoot(roots, fileUploadInfo.getImageBasePath());
        addRoot(roots, fileUploadInfo.getAssetBasePath());
        addRoot(roots, fileUploadInfo.getLegacyAssetBasePath());
        if (roots.isEmpty()) {
            throw new IllegalStateException("At least one runtime media root must be configured");
        }
        return List.copyOf(roots);
    }

    private static void addRoot(Set<Path> roots, String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return;
        roots.add(Path.of(configuredPath).toAbsolutePath().normalize());
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1).toLowerCase();
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static Map<Integer, String> activityImages() {
        Map<Integer, String> images = new LinkedHashMap<>();
        for (int id = 1; id <= 12; id++) images.put(id, "/image/20240919/" + id + ".png");
        images.put(13, "/image/20240919/14.png");
        images.put(14, "/image/20240919/13.png");
        images.put(15, "/image/20240919/16.png");
        return Map.copyOf(images);
    }

    private static Map<Integer, String> newsImages() {
        return Map.of(
                5, "/image/20250430/f6ee35681c9c4045b6dcccb52afa3998.png",
                6, "/image/20250430/2757166172d84ec38563fc710c413c06.png",
                7, "/image/20250430/86372a160ba2407d82363ed98066aeb4.png");
    }

    private static Map<Integer, String> recipeImages() {
        return Map.of(
                2, "/image/20260430/2b1e58f0a1a8493696d58c00f15bb1dc.png",
                3, "/image/20260430/8001357b86af4903addd5e813ba010b1.png",
                4, "/image/20260430/db135a6ca652498f9dc8d9c4429383a5.png");
    }

    private static String legacyServiceImage(int id) {
        if (id == 1 || id >= 6 && id <= 9) return "/image/demo/medical.svg";
        if (id == 2 || id >= 10 && id <= 14) return "/image/demo/daily-care.svg";
        if (id == 3 || id >= 15 && id <= 18) return "/image/demo/rehabilitation.svg";
        if (id == 4 || id >= 19 && id <= 22) return "/image/demo/companionship.svg";
        return "/image/demo/recreation.svg";
    }
}
