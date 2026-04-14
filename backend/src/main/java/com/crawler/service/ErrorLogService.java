package com.crawler.service;

import com.crawler.mapper.ErrorLogMapper;
import com.crawler.model.entity.ErrorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogService.class);

    private final ErrorLogMapper errorLogMapper;

    public ErrorLogService(ErrorLogMapper errorLogMapper) {
        this.errorLogMapper = errorLogMapper;
    }

    /**
     * 记录错误到数据库，排除数据库连接错误
     */
    public void logError(String errorType, String errorDetail) {
        // 排除数据库连接相关错误，避免死循环
        if (isDbConnectionError(errorDetail)) {
            log.warn("跳过数据库连接错误的记录: {}", errorDetail);
            return;
        }

        try {
            ErrorLog entry = new ErrorLog();
            entry.setErrorType(errorType);
            // 截断过长的错误信息
            entry.setErrorDetail(errorDetail != null && errorDetail.length() > 5000
                    ? errorDetail.substring(0, 5000) : errorDetail);
            errorLogMapper.insert(entry);
        } catch (Exception e) {
            // 记录错误日志本身失败时，只打日志不抛异常
            log.error("写入错误日志失败: {}", e.getMessage());
        }
    }

    public void logError(String errorType, Throwable ex) {
        String detail = ex.getClass().getName() + ": " + ex.getMessage();
        logError(errorType, detail);
    }

    private boolean isDbConnectionError(String detail) {
        if (detail == null) return false;
        String lower = detail.toLowerCase();
        return lower.contains("communications link failure")
                || lower.contains("connection refused")
                || lower.contains("cannot acquire connection")
                || lower.contains("unable to acquire jdbc connection")
                || lower.contains("access denied for user")
                || lower.contains("unknown database");
    }
}
