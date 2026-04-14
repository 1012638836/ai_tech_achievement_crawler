package com.crawler.controller;

import com.crawler.mapper.CrawlTaskMapper;
import com.crawler.model.dto.ApiResult;
import com.crawler.model.dto.PageResult;
import com.crawler.model.entity.CrawlTask;
import com.crawler.service.CrawlService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final CrawlTaskMapper taskMapper;
    private final CrawlService crawlService;

    public TaskController(CrawlTaskMapper taskMapper, CrawlService crawlService) {
        this.taskMapper = taskMapper;
        this.crawlService = crawlService;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<CrawlTask>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<CrawlTask> list = taskMapper.selectPage(offset, size);
        long total = taskMapper.countAll();
        return ApiResult.ok(PageResult.of(list, total, page, size));
    }

    @PostMapping("/{id}/stop")
    public ApiResult<Boolean> stop(@PathVariable Long id) {
        boolean success = crawlService.stopTask(id);
        if (success) {
            return ApiResult.ok(true);
        }
        return ApiResult.error("任务未在运行中，无法停止");
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        CrawlTask task = taskMapper.selectById(id);
        if (task == null) {
            return ApiResult.error("任务不存在");
        }
        if ("RUNNING".equals(task.getStatus())) {
            return ApiResult.error("运行中的任务无法删除，请先停止");
        }
        taskMapper.deleteById(id);
        return ApiResult.ok(true);
    }

    @PostMapping("/{id}/retry")
    public ApiResult<Long> retry(@PathVariable Long id) {
        CrawlTask oldTask = taskMapper.selectById(id);
        if (oldTask == null) {
            return ApiResult.error("任务不存在");
        }
        if (!"FAILED".equals(oldTask.getStatus()) && !"STOPPED".equals(oldTask.getStatus())) {
            return ApiResult.error("只能重试失败或已停止的任务");
        }
        if (oldTask.getSourceId() == null) {
            return ApiResult.error("任务无关联数据源");
        }

        // 创建新任务，从中断处继续
        CrawlTask newTask = new CrawlTask();
        newTask.setSourceId(oldTask.getSourceId());
        newTask.setType(oldTask.getType());
        newTask.setStatus("PENDING");
        taskMapper.insert(newTask);

        crawlService.executeCrawlTask(oldTask.getSourceId(), newTask.getId());
        return ApiResult.ok(newTask.getId());
    }
}
