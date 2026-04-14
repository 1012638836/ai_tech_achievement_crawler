package com.crawler.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SourceSite {
    private Long id;
    private String school;
    private String listUrl;
    private Integer status; // 0-待分析 1-规则就绪 2-采集中 3-异常
    private LocalDateTime lastCrawlTime;
    private Integer totalCrawled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
