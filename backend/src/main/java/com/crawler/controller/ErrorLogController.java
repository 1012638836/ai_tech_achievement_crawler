package com.crawler.controller;

import com.crawler.mapper.ErrorLogMapper;
import com.crawler.model.dto.ApiResult;
import com.crawler.model.dto.PageResult;
import com.crawler.model.entity.ErrorLog;
import com.crawler.service.ErrorLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/error-log")
public class ErrorLogController {

    private final ErrorLogMapper errorLogMapper;
    private final ErrorLogService errorLogService;

    public ErrorLogController(ErrorLogMapper errorLogMapper, ErrorLogService errorLogService) {
        this.errorLogMapper = errorLogMapper;
        this.errorLogService = errorLogService;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<ErrorLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<ErrorLog> list = errorLogMapper.selectPage(offset, size);
        long total = errorLogMapper.countAll();
        return ApiResult.ok(PageResult.of(list, total, page, size));
    }

    /**
     * 前端上报错误
     */
    @PostMapping("/report")
    public ApiResult<Void> report(@RequestBody Map<String, String> body) {
        String errorType = body.getOrDefault("errorType", "前端错误");
        String errorDetail = body.getOrDefault("errorDetail", "");
        errorLogService.logError(errorType, errorDetail);
        return ApiResult.ok(null);
    }
}
