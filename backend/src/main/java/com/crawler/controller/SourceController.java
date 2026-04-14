package com.crawler.controller;

import com.crawler.mapper.CrawlTaskMapper;
import com.crawler.mapper.SourceSiteMapper;
import com.crawler.model.dto.ApiResult;
import com.crawler.model.dto.CrawlRuleDTO;
import com.crawler.model.dto.PageResult;
import com.crawler.model.dto.RulePreviewDTO;
import com.crawler.model.entity.CrawlRule;
import com.crawler.model.entity.CrawlTask;
import com.crawler.model.entity.SourceSite;
import com.crawler.service.CrawlService;
import com.crawler.service.RuleGenerateService;
import com.crawler.service.SourceService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/source")
public class SourceController {

    private final SourceService sourceService;
    private final RuleGenerateService ruleGenerateService;
    private final CrawlService crawlService;
    private final CrawlTaskMapper taskMapper;
    private final SourceSiteMapper sourceMapper;

    public SourceController(SourceService sourceService, RuleGenerateService ruleGenerateService,
                            CrawlService crawlService, CrawlTaskMapper taskMapper, SourceSiteMapper sourceMapper) {
        this.sourceService = sourceService;
        this.ruleGenerateService = ruleGenerateService;
        this.crawlService = crawlService;
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
    }

    @PostMapping("/import")
    public ApiResult<Integer> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            int count = sourceService.importFromExcel(file);
            return ApiResult.ok(count);
        } catch (Exception e) {
            return ApiResult.error("导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResult<PageResult<SourceSite>> list(
            @RequestParam(required = false) String school,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(sourceService.listPage(school, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResult<SourceSite> getById(@PathVariable Long id) {
        return ApiResult.ok(sourceService.getById(id));
    }

    @PostMapping("/{id}/analyze")
    public ApiResult<CrawlRule> analyze(@PathVariable Long id) {
        try {
            CrawlRule rule = ruleGenerateService.analyzeAndGenerateRule(id);
            return ApiResult.ok(rule);
        } catch (Exception e) {
            return ApiResult.error("规则分析失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/crawl")
    public ApiResult<Long> crawl(@PathVariable Long id) {
        CrawlTask task = new CrawlTask();
        task.setSourceId(id);
        task.setType("FULL");
        task.setStatus("PENDING");
        taskMapper.insert(task);

        crawlService.executeCrawlTask(id, task.getId());
        return ApiResult.ok(task.getId());
    }

    @GetMapping("/rule/{sourceId}")
    public ApiResult<CrawlRule> getRule(@PathVariable Long sourceId) {
        return ApiResult.ok(ruleGenerateService.getRuleBySourceId(sourceId));
    }

    @PostMapping("/{id}/preview-rule")
    public ApiResult<RulePreviewDTO> previewRule(@PathVariable Long id) {
        try {
            RulePreviewDTO preview = ruleGenerateService.previewRule(id);
            return ApiResult.ok(preview);
        } catch (Exception e) {
            return ApiResult.error("规则预览失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/confirm-rule")
    public ApiResult<CrawlRule> confirmRule(@PathVariable Long id, @RequestBody CrawlRuleDTO dto) {
        try {
            CrawlRule rule = ruleGenerateService.confirmRule(id, dto);
            return ApiResult.ok(rule);
        } catch (Exception e) {
            return ApiResult.error("规则确认失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/test-rule")
    public ApiResult<RulePreviewDTO> testRule(@PathVariable Long id, @RequestBody CrawlRuleDTO dto) {
        try {
            RulePreviewDTO preview = ruleGenerateService.testRule(id, dto);
            return ApiResult.ok(preview);
        } catch (Exception e) {
            return ApiResult.error("规则测试失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        SourceSite source = sourceMapper.selectById(id);
        if (source == null) {
            return ApiResult.error("数据源不存在");
        }
        if (source.getStatus() == 2) {
            return ApiResult.error("采集中的数据源不能删除");
        }
        sourceMapper.deleteById(id);
        return ApiResult.ok(true);
    }
}
