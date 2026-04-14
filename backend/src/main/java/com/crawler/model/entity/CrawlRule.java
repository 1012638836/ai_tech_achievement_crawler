package com.crawler.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CrawlRule {
    private Long id;
    private Long sourceId;
    private String articleSelector;
    private String articleUrlAttr;
    private String titleSelector;
    private String nextPageSelector;
    private String nextPageUrlAttr;
    private String contentSelector;
    private String urlPattern;
    private String paginationType;  // URL_PATTERN / CSS_SELECTOR / API / BROWSER
    private String apiUrl;
    private String apiMethod;
    private String apiPageParam;
    private String apiDataPath;
    private String apiTitleField;
    private String apiUrlField;
    private String browserNextBtn;
    private String aiRawResponse;
    private Integer verified;
    private LocalDateTime createdAt;
}
