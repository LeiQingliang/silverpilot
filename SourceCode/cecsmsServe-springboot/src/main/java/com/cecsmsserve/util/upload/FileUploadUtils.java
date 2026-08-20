package com.cecsmsserve.util.upload;

import com.cecsmsserve.util.result.CommonResult;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FileUploadUtils {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<String, Long> MAX_BYTES = Map.of(
            "image", 10L * 1024 * 1024,
            "video", 100L * 1024 * 1024,
            "file", 20L * 1024 * 1024);

    private FileUploadUtils() {
    }

    public static CommonResult<String> upload(
            MultipartFile multipartFile,
            String configuredBasePath,
            String category) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return CommonResult.validateFailed("上传文件不能为空");
        }
        if (!MAX_BYTES.containsKey(category)) {
            return CommonResult.validateFailed("上传类型无效");
        }
        if (multipartFile.getSize() > MAX_BYTES.get(category)) {
            return CommonResult.validateFailed("文件超过允许的大小");
        }

        String originalName = multipartFile.getOriginalFilename();
        String extension = allowedExtension(category, extensionOf(originalName));
        if (extension == null) {
            return CommonResult.validateFailed("不支持该文件格式");
        }
        if ("image".equals(category) && !isReadableImage(multipartFile)) {
            return CommonResult.validateFailed("图片内容无效");
        }

        Path basePath = Path.of(configuredBasePath).toAbsolutePath().normalize();
        String datePath = LocalDate.now().format(DATE_PATH);
        Path destinationDirectory = basePath.resolve(category).resolve(datePath).normalize();
        if (!destinationDirectory.startsWith(basePath)) {
            return CommonResult.error("上传路径配置无效");
        }

        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path destination = destinationDirectory.resolve(newFileName).normalize();
        if (!destination.startsWith(destinationDirectory)) {
            return CommonResult.error("上传路径配置无效");
        }
        try {
            Files.createDirectories(destinationDirectory);
            try (InputStream input = multipartFile.getInputStream()) {
                Files.copy(input, destination);
            }
            return CommonResult.success("/" + category + "/" + datePath + "/" + newFileName);
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // Best-effort cleanup of an incomplete upload.
            }
            return CommonResult.error("文件保存失败");
        }
    }

    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        String normalizedName = originalName.replace('\\', '/');
        String safeName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot == safeName.length() - 1) {
            return "";
        }
        return safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String allowedExtension(String category, String candidate) {
        return switch (category) {
            case "image" -> switch (candidate) {
                case "jpg" -> "jpg";
                case "jpeg" -> "jpeg";
                case "png" -> "png";
                case "gif" -> "gif";
                default -> null;
            };
            case "video" -> switch (candidate) {
                case "mp4" -> "mp4";
                case "webm" -> "webm";
                default -> null;
            };
            case "file" -> switch (candidate) {
                case "pdf" -> "pdf";
                case "doc" -> "doc";
                case "docx" -> "docx";
                case "xls" -> "xls";
                case "xlsx" -> "xlsx";
                case "txt" -> "txt";
                default -> null;
            };
            default -> null;
        };
    }

    private static boolean isReadableImage(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return ImageIO.read(input) != null;
        } catch (IOException ex) {
            return false;
        }
    }
}
