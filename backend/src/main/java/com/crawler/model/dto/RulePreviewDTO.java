package com.crawler.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class RulePreviewDTO {
    private CrawlRuleDTO rule;
    private List<ArticlePreview> articles;
    private String nextPageUrl;
    private String sampleTitle;
    private String sampleContent;

    @Data
    public static class ArticlePreview {
        private String title;
        private String url;
    }
}
