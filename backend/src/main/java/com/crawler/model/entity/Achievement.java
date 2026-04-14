package com.crawler.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Achievement {
    private Long id;
    private Long linkId;
    private Long sourceId;
    private String title;
    private String content;
    private String url;
    private String school;
    private String field;
    private String journals;
    private String funders;
    private LocalDateTime publishTime;
    private String techKeywords;
    private String stage;
    private String domain;
    private String applicationScenario;
    private String aiRawResponse;
    private LocalDateTime createdAt;
}
