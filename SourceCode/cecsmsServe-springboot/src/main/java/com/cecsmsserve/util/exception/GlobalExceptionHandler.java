package com.cecsmsserve.util.exception;

import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResult<Void>> validationError(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数校验失败" : error.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(CommonResult.validateFailed(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResult<Void>> malformedBody() {
        return ResponseEntity.badRequest().body(CommonResult.validateFailed("请求内容格式无效"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResult<Void>> missingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(CommonResult.validateFailed("缺少必要参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CommonResult<Void>> missingMultipartPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest()
                .body(CommonResult.validateFailed("缺少必要的上传内容: " + ex.getRequestPartName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResult<Void>> argumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(CommonResult.validateFailed("参数格式无效: " + ex.getName()));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<CommonResult<Void>> methodValidationError() {
        return ResponseEntity.badRequest().body(CommonResult.validateFailed("请求参数校验失败"));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<CommonResult<Void>> bindingError(BindException ex) {
        String message = ex.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数绑定失败" : error.getDefaultMessage())
                .orElse("参数绑定失败");
        return ResponseEntity.badRequest().body(CommonResult.validateFailed(message));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResult<Void>> methodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new CommonResult<>(405, "请求方法不支持", null));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<CommonResult<Void>> resourceNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResult.notFound());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResult<Void>> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(CommonResult.validateFailed("上传文件超过服务器允许的大小"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CommonResult<Void>> dataConflict(DataIntegrityViolationException ex) {
        log.warn("Database constraint rejected a request ({})",
                ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(CommonResult.conflict("数据重复或关联关系无效"));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonResult<Void>> customError(CustomException ex) {
        return ResponseEntity.badRequest().body(CommonResult.validateFailed(ex.getMsg()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<Void>> unexpectedError(HttpServletRequest request, Exception ex) {
        String safeRequestUri = request.getRequestURI().replace('\r', '_').replace('\n', '_');
        log.error("Unhandled error for {} {}", request.getMethod(), safeRequestUri, ex);
        return ResponseEntity.internalServerError().body(CommonResult.error("系统异常，请稍后重试"));
    }
}
