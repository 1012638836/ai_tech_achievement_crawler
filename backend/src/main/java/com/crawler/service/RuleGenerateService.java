package com.crawler.service;

import com.crawler.mapper.CrawlRuleMapper;
import com.crawler.mapper.SourceSiteMapper;
import com.crawler.model.dto.CrawlRuleDTO;
import com.crawler.model.dto.RulePreviewDTO;
import com.crawler.model.entity.CrawlRule;
import com.crawler.model.entity.SourceSite;
import com.crawler.service.llm.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class RuleGenerateService {

    private static final Logger log = LoggerFactory.getLogger(RuleGenerateService.class);

    private final LlmService llmService;
    private final CrawlRuleMapper ruleMapper;
    private final SourceSiteMapper sourceMapper;
    private final ObjectMapper objectMapper;
    private final ErrorLogService errorLogService;

    @Value("${crawler.rule-max-retry:3}")
    private int ruleMaxRetry;

    @Value("${crawler.rule-min-links:3}")
    private int ruleMinLinks;

    public RuleGenerateService(LlmService llmService, CrawlRuleMapper ruleMapper,
                               SourceSiteMapper sourceMapper, ObjectMapper objectMapper,
                               ErrorLogService errorLogService) {
        this.llmService = llmService;
        this.ruleMapper = ruleMapper;
        this.sourceMapper = sourceMapper;
        this.objectMapper = objectMapper;
        this.errorLogService = errorLogService;
    }

    private static final String SYSTEM_PROMPT = """
            你是一个网页结构分析专家。分析给定的HTML页面，识别出文章列表和翻页元素的CSS选择器。
            严格按JSON格式输出，不要输出任何其他内容。
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            分析以下列表页HTML，提取CSS选择器规则，返回JSON格式：
            {
              "articleSelector": "文章链接的CSS选择器(选中a标签，用于提取URL)",
              "articleUrlAttr": "链接属性，通常是href",
              "titleSelector": "文章标题的CSS选择器(用于提取干净的标题文本)",
              "paginationType": "翻页类型(见下方说明)",
              "nextPageSelector": "下一页按钮/链接的CSS选择器",
              "nextPageUrlAttr": "下一页链接属性，通常是href",
              "contentSelector": "详情页正文区域的选择器(可留空)",
              "urlPattern": "翻页URL模板",
              "browserNextBtn": "JS渲染翻页时的下一页按钮CSS选择器"
            }

            注意：
            - articleSelector 和 titleSelector 是两个不同的选择器：
              - articleSelector 选中包含链接的a标签，用于提取href得到文章URL
              - titleSelector 选中包含干净标题文本的元素。很多网站a标签内嵌套了图片、日期、摘要等多余内容
              - 如果a标签的文本就是干净的标题，titleSelector可以和articleSelector相同
              - 如果a标签内嵌套了标题子元素(如<a><span class="title">标题</span>...</a>)，titleSelector应指向那个子元素
            - 排除导航栏、侧边栏、页脚等非正文链接
            - 翻页类型 paginationType 必须是以下之一：
              1. "URL_PATTERN" — URL有规律变化(如list1.htm→list2.htm 或 ?page=1→?page=2)，填urlPattern
              2. "CSS_SELECTOR" — 有静态a标签翻页链接(如<a href="/list2.htm">下一页</a>)，填nextPageSelector
              3. "BROWSER" — 翻页按钮需要JS点击(onclick事件、SPA路由等)，填browserNextBtn
              优先级：URL_PATTERN > CSS_SELECTOR > BROWSER
            - urlPattern说明：
              - 例如：/1020/list{page}.htm 表示页码替换{page}
              - 例如：/news?page={page} 表示query参数翻页
              - 很多高校网站翻页URL是 listN.htm 递减
            - browserNextBtn说明：
              - 当翻页按钮没有href(使用onclick或JS事件)时使用
              - 填写按钮的CSS选择器，系统会用无头浏览器点击它
            - 优先使用class/id选择器，确保选择器稳定可靠
            - 当前页面URL是: %s

            HTML内容：
            ---
            %s
            ---
            """;

    private static final String FEEDBACK_PROMPT_TEMPLATE = """
            上一次你给出的CSS选择器有问题，请根据反馈重新分析：

            问题：%s

            请重新分析HTML并返回修正后的JSON格式（格式同上）：
            {
              "articleSelector": "文章链接的CSS选择器(选中a标签，用于提取URL)",
              "articleUrlAttr": "链接属性，通常是href",
              "titleSelector": "文章标题的CSS选择器(选中包含干净标题文本的元素)",
              "paginationType": "翻页类型: URL_PATTERN/CSS_SELECTOR/BROWSER",
              "nextPageSelector": "下一页按钮/链接的CSS选择器",
              "nextPageUrlAttr": "下一页链接属性，通常是href",
              "contentSelector": "详情页正文区域的选择器，留空也可",
              "urlPattern": "翻页URL模板(如/1020/list{page}.htm)，留空也可",
              "browserNextBtn": "JS翻页按钮的CSS选择器，留空也可"
            }

            当前页面URL是: %s

            HTML内容：
            ---
            %s
            ---
            """;

    /**
     * 分析列表页HTML，用AI生成采集规则，带验证→反馈→重试机制
     */
    public CrawlRule analyzeAndGenerateRule(Long sourceId) throws Exception {
        SourceSite source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new RuntimeException("数据源不存在: " + sourceId);
        }

        Document doc = Jsoup.connect(source.getListUrl())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get();

        String html = doc.html();
        if (html.length() > 15000) {
            html = html.substring(0, 15000);
        }

        CrawlRuleDTO dto = null;
        String lastAiResponse = null;
        String lastError = null;

        for (int attempt = 0; attempt < ruleMaxRetry; attempt++) {
            String userPrompt;
            if (attempt == 0) {
                userPrompt = String.format(USER_PROMPT_TEMPLATE, source.getListUrl(), html);
            } else {
                userPrompt = String.format(FEEDBACK_PROMPT_TEMPLATE, lastError, source.getListUrl(), html);
            }

            lastAiResponse = llmService.chat(SYSTEM_PROMPT, userPrompt);
            log.info("AI规则分析响应 (第{}次): {}", attempt + 1, lastAiResponse);

            try {
                String jsonStr = extractJson(lastAiResponse);
                dto = objectMapper.readValue(jsonStr, CrawlRuleDTO.class);
            } catch (Exception e) {
                lastError = "AI返回的JSON格式无法解析: " + e.getMessage();
                log.warn("第{}次规则解析失败: {}", attempt + 1, lastError);
                continue;
            }

            // 验证 articleSelector
            String verifyError = verifyRule(doc, dto);
            if (verifyError == null) {
                log.info("第{}次规则验证通过", attempt + 1);
                break;
            }

            lastError = verifyError;
            log.warn("第{}次规则验证失败: {}", attempt + 1, lastError);
            dto = null; // 标记失败，进入下一轮
        }

        if (dto == null) {
            String msg = "AI经过" + ruleMaxRetry + "次尝试仍无法生成有效规则，最后错误: " + lastError;
            errorLogService.logError("规则生成失败", "sourceId=" + sourceId + ", " + msg);
            throw new RuntimeException(msg);
        }

        // 存入数据库
        CrawlRule rule = new CrawlRule();
        rule.setSourceId(sourceId);
        rule.setArticleSelector(dto.getArticleSelector());
        rule.setArticleUrlAttr(dto.getArticleUrlAttr() != null ? dto.getArticleUrlAttr() : "href");
        rule.setTitleSelector(dto.getTitleSelector());
        rule.setNextPageSelector(dto.getNextPageSelector());
        rule.setNextPageUrlAttr(dto.getNextPageUrlAttr() != null ? dto.getNextPageUrlAttr() : "href");
        rule.setContentSelector(dto.getContentSelector());
        rule.setUrlPattern(dto.getUrlPattern());
        rule.setPaginationType(dto.getPaginationType() != null ? dto.getPaginationType() : "URL_PATTERN");
        rule.setApiUrl(dto.getApiUrl());
        rule.setApiMethod(dto.getApiMethod());
        rule.setApiPageParam(dto.getApiPageParam());
        rule.setApiDataPath(dto.getApiDataPath());
        rule.setApiTitleField(dto.getApiTitleField());
        rule.setApiUrlField(dto.getApiUrlField());
        rule.setBrowserNextBtn(dto.getBrowserNextBtn());
        rule.setAiRawResponse(lastAiResponse);

        ruleMapper.insert(rule);
        sourceMapper.updateStatus(sourceId, 1);

        return rule;
    }

    /**
     * 验证AI生成的规则是否能在HTML上正常工作
     * @return null表示通过，否则返回错误描述
     */
    private String verifyRule(Document doc, CrawlRuleDTO dto) {
        if (dto.getArticleSelector() == null || dto.getArticleSelector().isBlank()) {
            return "articleSelector为空";
        }

        try {
            Elements articles = doc.select(dto.getArticleSelector());
            if (articles.isEmpty()) {
                return String.format("articleSelector '%s' 匹配到0个元素，应至少匹配到%d个文章链接",
                        dto.getArticleSelector(), ruleMinLinks);
            }
            if (articles.size() < ruleMinLinks) {
                return String.format("articleSelector '%s' 只匹配到%d个元素，数量太少（期望至少%d个），可能选择器不准确",
                        dto.getArticleSelector(), articles.size(), ruleMinLinks);
            }
        } catch (Exception e) {
            return String.format("articleSelector '%s' 语法错误: %s", dto.getArticleSelector(), e.getMessage());
        }

        // 验证 nextPageSelector（可选，但如果填了要能匹配到）
        if (dto.getNextPageSelector() != null && !dto.getNextPageSelector().isBlank()) {
            try {
                Elements nextPage = doc.select(dto.getNextPageSelector());
                if (nextPage.isEmpty()) {
                    log.warn("nextPageSelector '{}' 未匹配到元素，但不阻塞", dto.getNextPageSelector());
                }
            } catch (Exception e) {
                return String.format("nextPageSelector '%s' 语法错误: %s", dto.getNextPageSelector(), e.getMessage());
            }
        }

        return null;
    }

    /**
     * 预览规则：生成规则+提取示例数据，但不保存到数据库
     */
    public RulePreviewDTO previewRule(Long sourceId) throws Exception {
        SourceSite source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new RuntimeException("数据源不存在: " + sourceId);
        }

        Document doc = Jsoup.connect(source.getListUrl())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get();

        String html = doc.html();
        String truncatedHtml = html.length() > 15000 ? html.substring(0, 15000) : html;

        CrawlRuleDTO dto = null;
        String lastError = null;

        for (int attempt = 0; attempt < ruleMaxRetry; attempt++) {
            String userPrompt;
            if (attempt == 0) {
                userPrompt = String.format(USER_PROMPT_TEMPLATE, source.getListUrl(), truncatedHtml);
            } else {
                userPrompt = String.format(FEEDBACK_PROMPT_TEMPLATE, lastError, source.getListUrl(), truncatedHtml);
            }

            String aiResponse = llmService.chat(SYSTEM_PROMPT, userPrompt);
            log.info("AI规则预览响应 (第{}次): {}", attempt + 1, aiResponse);

            try {
                String jsonStr = extractJson(aiResponse);
                dto = objectMapper.readValue(jsonStr, CrawlRuleDTO.class);
            } catch (Exception e) {
                lastError = "AI返回的JSON格式无法解析: " + e.getMessage();
                log.warn("第{}次规则解析失败: {}", attempt + 1, lastError);
                continue;
            }

            String verifyError = verifyRule(doc, dto);
            if (verifyError == null) {
                log.info("第{}次规则验证通过", attempt + 1);
                break;
            }

            lastError = verifyError;
            log.warn("第{}次规则验证失败: {}", attempt + 1, lastError);
            dto = null;
        }

        if (dto == null) {
            String msg = "AI经过" + ruleMaxRetry + "次尝试仍无法生成有效规则，最后错误: " + lastError;
            errorLogService.logError("规则预览失败", "sourceId=" + sourceId + ", " + msg);
            throw new RuntimeException(msg);
        }

        // 清理所有CSS选择器中的格式问题
        dto.setArticleSelector(sanitizeSelector(dto.getArticleSelector()));
        dto.setTitleSelector(sanitizeSelector(dto.getTitleSelector()));
        dto.setContentSelector(sanitizeSelector(dto.getContentSelector()));
        dto.setNextPageSelector(sanitizeSelector(dto.getNextPageSelector()));
        dto.setBrowserNextBtn(sanitizeSelector(dto.getBrowserNextBtn()));

        // 用规则提取预览数据
        RulePreviewDTO preview = new RulePreviewDTO();
        preview.setRule(dto);

        // 提取文章标题+链接
        String baseUrl = source.getListUrl();
        List<RulePreviewDTO.ArticlePreview> articles = new ArrayList<>();
        Elements articleElements = doc.select(dto.getArticleSelector());
        String titleSel = dto.getTitleSelector();
        boolean useSeparateTitle = titleSel != null && !titleSel.isBlank()
                && !titleSel.equals(dto.getArticleSelector());

        // 如果titleSelector独立于articleSelector，单独查询标题元素列表
        Elements titleElements = null;
        if (useSeparateTitle) {
            titleElements = doc.select(titleSel);
        }

        for (int i = 0; i < articleElements.size(); i++) {
            Element el = articleElements.get(i);
            RulePreviewDTO.ArticlePreview ap = new RulePreviewDTO.ArticlePreview();

            // 标题：优先用titleSelector
            if (useSeparateTitle && titleElements != null && i < titleElements.size()) {
                ap.setTitle(titleElements.get(i).text());
            } else if (useSeparateTitle) {
                // titleSelector是articleSelector的子选择器，在a标签内部查找
                Element titleEl = el.selectFirst(titleSel);
                ap.setTitle(titleEl != null ? titleEl.text() : el.text());
            } else {
                ap.setTitle(el.text());
            }

            String href = el.attr(dto.getArticleUrlAttr() != null ? dto.getArticleUrlAttr() : "href");
            ap.setUrl(resolveUrl(baseUrl, href));
            articles.add(ap);
        }
        preview.setArticles(articles);

        // 提取下一页URL
        if (dto.getUrlPattern() != null && !dto.getUrlPattern().isBlank()) {
            preview.setNextPageUrl("URL模板翻页: " + dto.getUrlPattern());
        } else if (dto.getNextPageSelector() != null && !dto.getNextPageSelector().isBlank()) {
            Elements nextPage = doc.select(dto.getNextPageSelector());
            if (!nextPage.isEmpty()) {
                String nextHref = nextPage.first().attr(dto.getNextPageUrlAttr() != null ? dto.getNextPageUrlAttr() : "href");
                preview.setNextPageUrl(resolveUrl(baseUrl, nextHref));
            }
        }

        // 抓取第一篇文章的正文预览
        if (!articles.isEmpty() && articles.get(0).getUrl() != null && !articles.get(0).getUrl().isBlank()) {
            try {
                String firstUrl = articles.get(0).getUrl();
                Document detailDoc = Jsoup.connect(firstUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get();

                preview.setSampleTitle(articles.get(0).getTitle());

                // 如果有contentSelector，优先用它提取正文
                String content;
                if (dto.getContentSelector() != null && !dto.getContentSelector().isBlank()) {
                    Elements contentEls = detailDoc.select(dto.getContentSelector());
                    content = contentEls.isEmpty() ? detailDoc.body().text() : contentEls.text();
                } else {
                    content = detailDoc.body().text();
                }

                // 截取前500字
                preview.setSampleContent(content.length() > 500 ? content.substring(0, 500) + "..." : content);
            } catch (Exception e) {
                log.warn("抓取详情页预览失败: {}", e.getMessage());
                preview.setSampleContent("详情页抓取失败: " + e.getMessage());
            }
        }

        return preview;
    }

    /**
     * 确认规则：管理人员确认后保存到数据库
     */
    @Transactional
    public CrawlRule confirmRule(Long sourceId, CrawlRuleDTO dto) {
        SourceSite source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new RuntimeException("数据源不存在: " + sourceId);
        }

        CrawlRule rule = new CrawlRule();
        rule.setSourceId(sourceId);
        rule.setArticleSelector(dto.getArticleSelector());
        rule.setArticleUrlAttr(dto.getArticleUrlAttr() != null ? dto.getArticleUrlAttr() : "href");
        rule.setTitleSelector(dto.getTitleSelector());
        rule.setNextPageSelector(dto.getNextPageSelector());
        rule.setNextPageUrlAttr(dto.getNextPageUrlAttr() != null ? dto.getNextPageUrlAttr() : "href");
        rule.setContentSelector(dto.getContentSelector());
        rule.setUrlPattern(dto.getUrlPattern());
        rule.setPaginationType(dto.getPaginationType() != null ? dto.getPaginationType() : "URL_PATTERN");
        rule.setApiUrl(dto.getApiUrl());
        rule.setApiMethod(dto.getApiMethod());
        rule.setApiPageParam(dto.getApiPageParam());
        rule.setApiDataPath(dto.getApiDataPath());
        rule.setApiTitleField(dto.getApiTitleField());
        rule.setApiUrlField(dto.getApiUrlField());
        rule.setBrowserNextBtn(dto.getBrowserNextBtn());

        // 先删旧规则再插入新的
        ruleMapper.deleteBySourceId(sourceId);
        ruleMapper.insert(rule);
        sourceMapper.updateStatus(sourceId, 1);

        return rule;
    }

    /**
     * 测试规则：用传入的规则抓取页面，返回预览数据（不保存，不调用AI）
     */
    public RulePreviewDTO testRule(Long sourceId, CrawlRuleDTO dto) throws Exception {
        SourceSite source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new RuntimeException("数据源不存在: " + sourceId);
        }

        String listUrl = source.getListUrl();
        if (listUrl == null || listUrl.isBlank()) {
            throw new RuntimeException("数据源列表页URL为空，请先配置列表页URL");
        }

        // 清理所有CSS选择器中的格式问题
        dto.setArticleSelector(sanitizeSelector(dto.getArticleSelector()));
        dto.setTitleSelector(sanitizeSelector(dto.getTitleSelector()));
        dto.setContentSelector(sanitizeSelector(dto.getContentSelector()));
        dto.setNextPageSelector(sanitizeSelector(dto.getNextPageSelector()));
        dto.setBrowserNextBtn(sanitizeSelector(dto.getBrowserNextBtn()));

        Document doc = Jsoup.connect(listUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get();

        RulePreviewDTO preview = new RulePreviewDTO();
        preview.setRule(dto);

        // 提取文章标题+链接
        String baseUrl = source.getListUrl();
        List<RulePreviewDTO.ArticlePreview> articles = new ArrayList<>();

        if (dto.getArticleSelector() != null && !dto.getArticleSelector().isBlank()) {
            Elements articleElements = doc.select(dto.getArticleSelector());
            String titleSel = dto.getTitleSelector();
            boolean useSeparateTitle = titleSel != null && !titleSel.isBlank()
                    && !titleSel.equals(dto.getArticleSelector());
            Elements titleElements = useSeparateTitle ? doc.select(titleSel) : null;

            for (int i = 0; i < articleElements.size(); i++) {
                Element el = articleElements.get(i);
                RulePreviewDTO.ArticlePreview ap = new RulePreviewDTO.ArticlePreview();

                if (useSeparateTitle && titleElements != null && i < titleElements.size()) {
                    ap.setTitle(titleElements.get(i).text());
                } else if (useSeparateTitle) {
                    Element titleEl = el.selectFirst(titleSel);
                    ap.setTitle(titleEl != null ? titleEl.text() : el.text());
                } else {
                    ap.setTitle(el.text());
                }

                String href = el.attr(dto.getArticleUrlAttr() != null ? dto.getArticleUrlAttr() : "href");
                ap.setUrl(resolveUrl(baseUrl, href));
                articles.add(ap);
            }
        }
        preview.setArticles(articles);

        // 翻页信息
        String paginationType = dto.getPaginationType() != null ? dto.getPaginationType() : "URL_PATTERN";
        switch (paginationType) {
            case "URL_PATTERN":
                if (dto.getUrlPattern() != null && !dto.getUrlPattern().isBlank()) {
                    String nextUrl = dto.getUrlPattern().replace("{page}", "2");
                    if (!nextUrl.startsWith("http")) {
                        nextUrl = resolveUrl(source.getListUrl(), nextUrl);
                    }
                    preview.setNextPageUrl(nextUrl);
                }
                break;
            case "CSS_SELECTOR":
                if (dto.getNextPageSelector() != null && !dto.getNextPageSelector().isBlank()) {
                    Elements nextPage = doc.select(dto.getNextPageSelector());
                    if (!nextPage.isEmpty()) {
                        String nextHref = nextPage.first().attr(
                                dto.getNextPageUrlAttr() != null ? dto.getNextPageUrlAttr() : "href");
                        preview.setNextPageUrl(resolveUrl(baseUrl, nextHref));
                    } else {
                        preview.setNextPageUrl("选择器未匹配到下一页元素");
                    }
                }
                break;
            case "API":
                if (dto.getApiUrl() != null && !dto.getApiUrl().isBlank()) {
                    preview.setNextPageUrl("API翻页: " + dto.getApiUrl() + " (参数: " +
                            (dto.getApiPageParam() != null ? dto.getApiPageParam() : "page") + ")");
                }
                break;
            case "BROWSER":
                if (dto.getBrowserNextBtn() != null && !dto.getBrowserNextBtn().isBlank()) {
                    preview.setNextPageUrl("浏览器点击翻页: " + dto.getBrowserNextBtn());
                }
                break;
        }

        // 抓取第一篇文章的正文预览
        if (!articles.isEmpty() && articles.get(0).getUrl() != null && !articles.get(0).getUrl().isBlank()) {
            try {
                String firstUrl = articles.get(0).getUrl();
                Document detailDoc = Jsoup.connect(firstUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get();

                preview.setSampleTitle(articles.get(0).getTitle());

                String content;
                if (dto.getContentSelector() != null && !dto.getContentSelector().isBlank()) {
                    Elements contentEls = detailDoc.select(dto.getContentSelector());
                    content = contentEls.isEmpty() ? detailDoc.body().text() : contentEls.text();
                } else {
                    content = detailDoc.body().text();
                }
                preview.setSampleContent(content.length() > 500 ? content.substring(0, 500) + "..." : content);
            } catch (Exception e) {
                log.warn("测试规则抓取详情页失败: {}", e.getMessage());
                preview.setSampleContent("详情页抓取失败: " + e.getMessage());
            }
        }

        return preview;
    }

    /**
     * 清理CSS选择器：修复常见格式问题（如 ". className" → ".className"）
     */
    private String sanitizeSelector(String selector) {
        if (selector == null || selector.isBlank()) return selector;
        // 修复 ". className" → ".className"，"# idName" → "#idName"
        return selector.replaceAll("([.#])\\s+", "$1").trim();
    }

    /**
     * 将相对URL转为绝对URL
     */
    private String resolveUrl(String baseUrl, String href) {
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http")) return href;
        try {
            return new URI(baseUrl).resolve(href).toString();
        } catch (Exception e) {
            return href;
        }
    }

    public CrawlRule getRuleBySourceId(Long sourceId) {
        return ruleMapper.selectBySourceId(sourceId);
    }

    /**
     * 从AI响应中提取JSON（处理markdown代码块包裹的情况）
     */
    private String extractJson(String text) {
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}
