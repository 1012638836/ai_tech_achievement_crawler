package com.crawler.controller;

import com.crawler.mapper.AchievementMapper;
import com.crawler.mapper.AchievementResearcherMapper;
import com.crawler.model.dto.ApiResult;
import com.crawler.model.dto.PageResult;
import com.crawler.model.entity.Achievement;
import com.crawler.model.entity.AchievementResearcher;
import com.crawler.service.StructureService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/achievement")
public class AchievementController {

    private final AchievementMapper achievementMapper;
    private final AchievementResearcherMapper researcherMapper;
    private final StructureService structureService;

    public AchievementController(AchievementMapper achievementMapper,
                                 AchievementResearcherMapper researcherMapper,
                                 StructureService structureService) {
        this.achievementMapper = achievementMapper;
        this.researcherMapper = researcherMapper;
        this.structureService = structureService;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<Achievement>> list(
            @RequestParam(required = false) String school,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Achievement> list = achievementMapper.selectPage(school, domain, stage, keyword, offset, size);
        long total = achievementMapper.countTotal(school, domain, stage, keyword);
        return ApiResult.ok(PageResult.of(list, total, page, size));
    }

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> getById(@PathVariable Long id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            return ApiResult.error(404, "成果不存在");
        }
        List<AchievementResearcher> researchers = researcherMapper.selectByAchievementId(id);

        Map<String, Object> result = new HashMap<>();
        result.put("achievement", achievement);
        result.put("researchers", researchers);
        return ApiResult.ok(result);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        achievementMapper.deleteResearchersById(id);
        achievementMapper.deleteById(id);
        return ApiResult.ok();
    }

    @PostMapping("/fix-affiliations")
    public ApiResult<String> fixAffiliations() {
        structureService.fixExistingAffiliations();
        return ApiResult.ok("已启动存量数据修正");
    }
}
