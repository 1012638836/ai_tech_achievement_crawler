package com.crawler.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CrawlTask {
    private Long id;
    private Long sourceId;
    private String type; // FULL/INCREMENTAL/RULE_GEN
    private String status; // PENDING/RUNNING/SUCCESS/FAILED
    private Integer totalFound;
    private Integer totalCrawled;
    private Integer totalStructured;
    private String failReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
