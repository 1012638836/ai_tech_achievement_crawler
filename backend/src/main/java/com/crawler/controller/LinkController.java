package com.crawler.controller;

import com.crawler.mapper.AchievementLinkMapper;
import com.crawler.model.dto.ApiResult;
import com.crawler.model.dto.PageResult;
import com.crawler.model.entity.AchievementLink;
import com.crawler.service.CrawlService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/link")
public class LinkController {

    private final AchievementLinkMapper linkMapper;
    private final CrawlService crawlService;

    public LinkController(AchievementLinkMapper linkMapper, CrawlService crawlService) {
        this.linkMapper = linkMapper;
        this.crawlService = crawlService;
    }

    /**
     * 分页查看链接及AI判断结果
     */
    @GetMapping("/list")
    public ApiResult<PageResult<AchievementLink>> list(
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Integer isAchievement,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<AchievementLink> list = linkMapper.selectPage(sourceId, isAchievement, keyword, offset, size);
        long total = linkMapper.countPageTotal(sourceId, isAchievement, keyword);
        return ApiResult.ok(PageResult.of(list, total, page, size));
    }

    /**
     * 人工纠正AI判断结果
     */
    @PostMapping("/{id}/judge")
    public ApiResult<Void> manualJudge(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer isAchievement = (Integer) body.get("isAchievement");
        String reason = (String) body.getOrDefault("reason", "人工纠正");
        linkMapper.updateJudgement(id, isAchievement, reason);
        return ApiResult.ok();
    }

    /**
     * 批量结构化：把所有is_achievement=1且status=0的链接抓取详情页并结构化
     */
    @PostMapping("/batch-structure")
    public ApiResult<Long> batchStructure() {
        long pending = linkMapper.countPendingAchievements();
        if (pending == 0) {
            return ApiResult.error("没有待结构化的科研成果链接");
        }
        crawlService.executeBatchStructure();
        return ApiResult.ok(pending);
    }
}
