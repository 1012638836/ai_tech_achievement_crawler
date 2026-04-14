package com.crawler.model.dto;

import lombok.Data;
import java.util.List;

/**
 * AI结构化返回的JSON对应DTO
 */
@Data
public class AchievementStructuredDTO {
    private String title;
    private String content;
    private String field;
    private List<ResearcherDTO> researchers;
    private String journals;
    private String funders;
    private String publishTime;
    private String techKeywords;
    private String stage;
    private String domain;
    private String applicationScenario;

    @Data
    public static class ResearcherDTO {
        private String name;
        private String affiliation;
        private String school;
        private String college;
    }
}
