package com.crawler.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AchievementLink {
    private Long id;
    private Long sourceId;
    private String url;
    private String title;
    private Integer isAchievement; // null-未判断 1-是科研成果 0-不是
    private String judgeReason;
    private Integer status; // 0-待采集 1-已采集 2-已结构化 3-失败
    private String failReason;
    private LocalDateTime discoveredAt;
    private LocalDateTime crawledAt;
}
