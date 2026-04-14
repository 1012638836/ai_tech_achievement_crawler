package com.crawler.controller;

import com.crawler.mapper.AchievementMapper;
import com.crawler.mapper.CrawlStatsMapper;
import com.crawler.mapper.SourceSiteMapper;
import com.crawler.model.dto.ApiResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final SourceSiteMapper sourceMapper;
    private final AchievementMapper achievementMapper;
    private final CrawlStatsMapper statsMapper;

    public DashboardController(SourceSiteMapper sourceMapper, AchievementMapper achievementMapper,
                               CrawlStatsMapper statsMapper) {
        this.sourceMapper = sourceMapper;
        this.achievementMapper = achievementMapper;
        this.statsMapper = statsMapper;
    }

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalSources", sourceMapper.countAll());
        data.put("totalAchievements", achievementMapper.countAll());
        data.put("todayAchievements", achievementMapper.countToday());
        return ApiResult.ok(data);
    }

    @GetMapping("/trend")
    public ApiResult<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return ApiResult.ok(statsMapper.selectTrendFromReal(startDate));
    }

    @GetMapping("/top-sources")
    public ApiResult<List<Map<String, Object>>> topSources(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return ApiResult.ok(statsMapper.selectTopSourcesFromReal(startDate, limit));
    }
}
