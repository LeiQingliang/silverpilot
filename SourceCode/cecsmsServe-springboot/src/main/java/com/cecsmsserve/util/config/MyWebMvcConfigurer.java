package com.cecsmsserve.util.config;

import com.cecsmsserve.util.upload.FileUploadInfo;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class MyWebMvcConfigurer implements WebMvcConfigurer {

    private final Path uploadBasePath;
    private final List<Path> assetBasePaths;

    public MyWebMvcConfigurer(FileUploadInfo fileUploadInfo) {
        this.uploadBasePath = Path.of(fileUploadInfo.getImageBasePath()).toAbsolutePath().normalize();
        List<Path> configuredAssetBasePaths = new ArrayList<>();
        addAssetBasePath(configuredAssetBasePaths, fileUploadInfo.getAssetBasePath());
        addAssetBasePath(configuredAssetBasePaths, fileUploadInfo.getLegacyAssetBasePath());
        this.assetBasePaths = List.copyOf(configuredAssetBasePaths);
        try {
            Files.createDirectories(uploadBasePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create upload directory: " + uploadBasePath, ex);
        }
        for (Path assetBasePath : assetBasePaths) {
            if (!Files.isDirectory(assetBasePath)) {
                throw new IllegalStateException("Configured read-only asset directory does not exist: " + assetBasePath);
            }
        }
    }

    private void addAssetBasePath(List<Path> paths, String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return;
        }
        Path normalizedPath = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!paths.contains(normalizedPath)) {
            paths.add(normalizedPath);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registerUploadDirectory(registry, "image");
        registerUploadDirectory(registry, "file");
        registerUploadDirectory(registry, "video");
    }

    private void registerUploadDirectory(ResourceHandlerRegistry registry, String category) {
        Path directory = uploadBasePath.resolve(category).normalize();
        if (!directory.startsWith(uploadBasePath)) {
            throw new IllegalStateException("Upload category resolved outside the configured directory: " + category);
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create upload category directory: " + directory, ex);
        }

        List<String> resourceLocations = new ArrayList<>();
        resourceLocations.add(directory.toUri().toString());
        for (Path assetBasePath : assetBasePaths) {
            Path assetDirectory = assetBasePath.resolve(category).normalize();
            if (!assetDirectory.startsWith(assetBasePath)) {
                throw new IllegalStateException("Asset category resolved outside the configured directory: " + category);
            }
            if (Files.isDirectory(assetDirectory) && !assetDirectory.equals(directory)) {
                resourceLocations.add(assetDirectory.toUri().toString());
            }
        }
        registry.addResourceHandler("/" + category + "/**")
                .addResourceLocations(resourceLocations.toArray(String[]::new))
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
