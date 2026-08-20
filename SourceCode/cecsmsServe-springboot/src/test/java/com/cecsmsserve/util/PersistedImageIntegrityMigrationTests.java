package com.cecsmsserve.util;

import com.cecsmsserve.util.upload.FileUploadInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistedImageIntegrityMigrationTests {

    @TempDir
    Path tempDir;

    @Test
    void restoresPackagedPlaceholdersAndRemainsIdempotent() throws Exception {
        JdbcTemplate jdbc = database("restores-images");
        createTables(jdbc);
        jdbc.update("INSERT INTO activity (id, image) VALUES (1, '/image/demo/activity.svg')");
        jdbc.update("INSERT INTO news (id, avatar) VALUES (5, '/image/demo/news.svg')");
        jdbc.update("INSERT INTO recipe (id, image_url) VALUES (5, '/image/demo/meal.svg')");
        jdbc.update("INSERT INTO recipe (id, image_url) VALUES (2, '/image/demo/meal.svg')");
        jdbc.update("INSERT INTO recipe (id, image_url) VALUES (99, '/image/recipe/fish.jpg')");
        jdbc.update("INSERT INTO service_type (id, image) VALUES (1, '/image/demo/medical.svg')");

        writePng("20240919/1.png");
        writePng("20250430/f6ee35681c9c4045b6dcccb52afa3998.png");
        writePng("20260430/2b1e58f0a1a8493696d58c00f15bb1dc.png");
        writePng("20260327/1.png");

        PersistedImageIntegrityMigration migration = migration(jdbc);
        migration.run(new DefaultApplicationArguments(new String[0]));
        migration.run(new DefaultApplicationArguments(new String[0]));

        assertEquals("/image/20240919/1.png", value(jdbc, "activity", "image", 1));
        assertEquals("/image/20250430/f6ee35681c9c4045b6dcccb52afa3998.png",
                value(jdbc, "news", "avatar", 5));
        assertEquals("/image/20260430/2b1e58f0a1a8493696d58c00f15bb1dc.png",
                value(jdbc, "recipe", "image_url", 2));
        assertEquals("/image/20260327/1.png", value(jdbc, "service_type", "image", 1));
        assertNull(value(jdbc, "recipe", "image_url", 5));
        assertNull(value(jdbc, "recipe", "image_url", 99));
    }

    @Test
    void rejectsPersistedPathsWithoutARealImage() {
        JdbcTemplate jdbc = database("rejects-missing-image");
        createTables(jdbc);
        jdbc.update("INSERT INTO activity (id, image) VALUES (88, '/image/missing.png')");

        PersistedImageIntegrityMigration migration = migration(jdbc);

        assertThrows(IllegalStateException.class,
                () -> migration.run(new DefaultApplicationArguments(new String[0])));
    }

    private JdbcTemplate database(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return new JdbcTemplate(dataSource);
    }

    private void createTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE activity (id INT PRIMARY KEY, image VARCHAR(255))");
        jdbc.execute("CREATE TABLE news (id INT PRIMARY KEY, avatar VARCHAR(255))");
        jdbc.execute("CREATE TABLE recipe (id INT PRIMARY KEY, image_url VARCHAR(255))");
        jdbc.execute("CREATE TABLE service_type (id INT PRIMARY KEY, image VARCHAR(255))");
    }

    private PersistedImageIntegrityMigration migration(JdbcTemplate jdbc) {
        FileUploadInfo fileUploadInfo = new FileUploadInfo();
        fileUploadInfo.setImageBasePath(tempDir.toString());
        return new PersistedImageIntegrityMigration(jdbc.getDataSource(), fileUploadInfo);
    }

    private void writePng(String relativePath) throws Exception {
        Path image = tempDir.resolve("image").resolve(relativePath);
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 0
        });
    }

    private String value(JdbcTemplate jdbc, String table, String column, int id) {
        return jdbc.queryForObject(
                "SELECT `" + column + "` FROM `" + table + "` WHERE id = ?", String.class, id);
    }
}
