package com.crawler.model.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CrawlStats {
    private Long id;
    private LocalDate statDate;
    private Long sourceId;
    private Integer newLinks;
    private Integer newAchievements;
    private LocalDateTime createdAt;
}
