package com.crawler.config;

import com.crawler.model.dto.ApiResult;
import com.crawler.service.ErrorLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorLogService errorLogService;

    public GlobalExceptionHandler(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<?> handleException(Exception ex) {
        log.error("全局异常捕获: ", ex);
        errorLogService.logError("系统异常", ex);
        return ApiResult.error("系统错误: " + ex.getMessage());
    }
}
