package com.kilikili.common.web;

import com.kilikili.common.api.ApiResponse;
import com.kilikili.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> business(BusinessException exception) {
        log.warn("业务请求被拒绝: {}", exception.getMessage());
        return new ApiResponse<>(400, exception.getMessage(), null, Instant.now());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> uploadSizeExceeded(MaxUploadSizeExceededException exception) {
        log.warn("上传文件大小超限: {}", exception.getMessage());
        return new ApiResponse<>(400, "上传文件大小超出限制", null, Instant.now());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiResponse<Void> unexpected(Exception exception) {
        log.error("未处理的服务器异常", exception);
        return new ApiResponse<>(500, "服务器处理请求失败", null, Instant.now());
    }
}