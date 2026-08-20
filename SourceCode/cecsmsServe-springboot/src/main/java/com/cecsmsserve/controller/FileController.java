package com.cecsmsserve.controller;

import com.cecsmsserve.util.result.CommonResult;
import com.cecsmsserve.util.JWTInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import com.cecsmsserve.util.upload.FileUploadInfo;
import com.cecsmsserve.util.upload.FileUploadUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/upload")
public class FileController {

    private final FileUploadInfo fileUploadInfo;

    public FileController(FileUploadInfo fileUploadInfo) {
        this.fileUploadInfo = fileUploadInfo;
    }

    @PostMapping("/file")
    public CommonResult<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        if (!isStaff(request)) {
            return CommonResult.forbidden();
        }
        return FileUploadUtils.upload(file, fileUploadInfo.getImageBasePath(), "file");
    }

    @PostMapping("/image")
    public CommonResult<String> uploadImage(
            @RequestParam("imageFile") MultipartFile imageFile,
            HttpServletRequest request) {
        if (!isStaff(request)) {
            return CommonResult.forbidden();
        }
        return FileUploadUtils.upload(imageFile, fileUploadInfo.getImageBasePath(), "image");
    }

    @PostMapping("/video")
    public CommonResult<String> uploadVideo(
            @RequestParam("videoFile") MultipartFile videoFile,
            HttpServletRequest request) {
        if (!isStaff(request)) {
            return CommonResult.forbidden();
        }
        return FileUploadUtils.upload(videoFile, fileUploadInfo.getImageBasePath(), "video");
    }

    private boolean isStaff(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && roleId >= 1 && roleId <= 3;
    }
}
