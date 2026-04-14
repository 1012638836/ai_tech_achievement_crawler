package com.crawler.model.dto;

import lombok.Data;

/**
 * AI分析列表页返回的规则DTO
 */
@Data
public class CrawlRuleDTO {
    private String articleSelector;
    private String articleUrlAttr;
    private String titleSelector;
    private String nextPageSelector;
    private String nextPageUrlAttr;
    private String contentSelector;
    private String urlPattern;
    private String paginationType;
    private String apiUrl;
    private String apiMethod;
    private String apiPageParam;
    private String apiDataPath;
    private String apiTitleField;
    private String apiUrlField;
    private String browserNextBtn;
}
