package com.crawler.model.entity;

import lombok.Data;

@Data
public class AchievementResearcher {
    private Long id;
    private Long achievementId;
    private String name;
    private String affiliation;
    private String school;
    private String college;
}
