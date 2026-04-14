package com.crawler.service;

import com.crawler.mapper.*;
import com.crawler.model.entity.*;
import com.crawler.service.llm.LlmService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final SourceSiteMapper sourceMapper;
    private final CrawlRuleMapper ruleMapper;
    private final AchievementLinkMapper linkMapper;
    private final CrawlTaskMapper taskMapper;
    private final StructureService structureService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final ErrorLogService errorLogService;
    private final PlaywrightService playwrightService;
    private final OkHttpClient httpClient;

    /** 正在运行的任务ID集合，用于停止控制 */
    private final Set<Long> runningTasks = ConcurrentHashMap.newKeySet();
    /** 被请求停止的任务ID集合 */
    private final Set<Long> stoppingTasks = ConcurrentHashMap.newKeySet();

    @Value("${crawler.request-delay-ms:3000}")
    private int requestDelayMs;

    @Value("${crawler.max-retry:3}")
    private int maxRetry;

    @Value("${crawler.max-pages:50}")
    private int maxPages;

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    };

    private final Random random = new Random();

    private static final String JUDGE_SYSTEM_PROMPT = """
            你是一个科技成果分类专家。判断给定的文章标题是否属于"具体介绍某项科研成果进展"的内容。
            属于科研成果的：具体描述某项研究发现、技术突破、实验成果、论文核心内容的文章（如"XX团队在XX领域取得重要进展""XX研究成果发表于Nature""发现XX新机制"）。
            不属于科研成果的：
            - 获奖表彰类：荣获XX奖、获XX称号、入选XX计划/榜单
            - 活动会议类：参加XX活动、亮相XX会议、出席XX论坛、学术讲座预告
            - 项目启动类：启动XX项目、签约XX合作、成立XX中心
            - 企业宣传类：企业介绍、公司动态、产品发布
            - 媒体报道类：被XX媒体报道、XX媒体专访
            - 资质许可类：获得临床许可、取得XX资格、通过XX认证
            - 行政通知类：招生通知、行政公告、人事任命、校园活动、招聘信息、教学安排
            - 文科类成果：哲学、经济学、法学、教育学、文学、历史学、管理学、艺术学相关研究
            - 基础理论类：纯基础数学（如数论、代数几何）、理论物理（如弦理论、量子引力）
            - 军事学类：国防军事相关研究
            关键判断标准：文章必须具体介绍了某项理工科/应用科学的科研工作的研究内容、方法和发现，而非通知性、宣传性、资质性信息，且不属于上述排除学科。
            严格按JSON数组格式输出，不要输出其他内容。
            """;

    private static final String JUDGE_USER_PROMPT_TEMPLATE = """
            判断以下文章标题是否为科研成果/科研进展，返回JSON数组，每个元素对应一个标题：
            [{"index": 0, "is_achievement": true/false, "reason": "简要理由(10字内)"}]

            标题列表：
            %s
            """;

    public CrawlService(SourceSiteMapper sourceMapper, CrawlRuleMapper ruleMapper,
                         AchievementLinkMapper linkMapper, CrawlTaskMapper taskMapper,
                         StructureService structureService, LlmService llmService,
                         ObjectMapper objectMapper, ErrorLogService errorLogService,
                         PlaywrightService playwrightService) {
        this.sourceMapper = sourceMapper;
        this.ruleMapper = ruleMapper;
        this.linkMapper = linkMapper;
        this.taskMapper = taskMapper;
        this.structureService = structureService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.errorLogService = errorLogService;
        this.playwrightService = playwrightService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 停止指定任务
     */
    public boolean stopTask(Long taskId) {
        if (runningTasks.contains(taskId)) {
            stoppingTasks.add(taskId);
            log.info("任务 {} 已标记为停止", taskId);
            return true;
        }
        // 内存中没有，回退检查数据库状态（处理竞态条件或应用重启的情况）
        CrawlTask task = taskMapper.selectById(taskId);
        if (task != null && "RUNNING".equals(task.getStatus())) {
            stoppingTasks.add(taskId);
            // 直接更新数据库状态为STOPPED
            task.setStatus("STOPPED");
            task.setFinishedAt(LocalDateTime.now());
            task.setFailReason("用户手动停止");
            taskMapper.update(task);
            // 恢复数据源状态为规则就绪
            sourceMapper.updateStatus(task.getSourceId(), 1);
            log.info("任务 {} 不在内存运行集合中，已直接更新数据库状态为STOPPED", taskId);
            return true;
        }
        return false;
    }

    /**
     * 检查任务是否被要求停止
     */
    private boolean shouldStop(Long taskId) {
        return stoppingTasks.contains(taskId);
    }

    /**
     * 查询任务是否正在运行
     */
    public boolean isRunning(Long taskId) {
        return runningTasks.contains(taskId);
    }

    /**
     * 异步执行全量采集任务
     */
    @Async("crawlExecutor")
    public void executeCrawlTask(Long sourceId, Long taskId) {
        runningTasks.add(taskId);

        CrawlTask task = new CrawlTask();
        task.setId(taskId);
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.update(task);

        try {
            SourceSite source = sourceMapper.selectById(sourceId);
            CrawlRule rule = ruleMapper.selectBySourceId(sourceId);

            if (rule == null) {
                throw new RuntimeException("采集规则不存在，请先分析规则");
            }

            sourceMapper.updateStatus(sourceId, 2); // 采集中

            // 第一阶段：采集列表页，提取详情链接和标题
            int totalFound = crawlListPages(source, rule, taskId);
            task.setTotalFound(totalFound);

            if (shouldStop(taskId)) {
                finishTask(task, "STOPPED", "用户手动停止", sourceId);
                return;
            }

            // 第二阶段：AI批量判断标题是否为科研成果
            judgeLinks(sourceId);

            if (shouldStop(taskId)) {
                finishTask(task, "STOPPED", "用户手动停止", sourceId);
                return;
            }

            // 第三阶段：只采集判定为科研成果的详情页并结构化
            int crawled = 0;
            int structured = 0;
            List<AchievementLink> pendingLinks = linkMapper.selectPendingBySource(sourceId, 500);

            for (AchievementLink link : pendingLinks) {
                if (shouldStop(taskId)) {
                    log.info("任务 {} 在详情页采集阶段被停止", taskId);
                    break;
                }

                try {
                    String html = fetchPage(link.getUrl());
                    linkMapper.updateStatus(link.getId(), 1);
                    crawled++;

                    structureService.structureAndSave(link, source, html);
                    linkMapper.updateStatus(link.getId(), 2);
                    structured++;

                    delay();
                } catch (Exception e) {
                    log.error("采集详情页失败: {} - {}", link.getUrl(), e.getMessage());
                    errorLogService.logError("详情页采集失败", link.getUrl() + ": " + e.getMessage());
                    linkMapper.updateFailed(link.getId(), e.getMessage());
                }
            }

            task.setTotalCrawled(crawled);
            task.setTotalStructured(structured);

            if (shouldStop(taskId)) {
                finishTask(task, "STOPPED", "用户手动停止", sourceId);
            } else {
                task.setStatus("SUCCESS");
                task.setFinishedAt(LocalDateTime.now());
                sourceMapper.updateStatus(sourceId, 1); // 回到规则就绪
                sourceMapper.updateCrawlInfo(sourceId, (int) linkMapper.countBySource(sourceId));
                taskMapper.update(task);
            }

        } catch (Exception e) {
            log.error("采集任务失败: sourceId={}", sourceId, e);
            errorLogService.logError("采集任务失败", "sourceId=" + sourceId + ", " + e.getMessage());
            task.setStatus("FAILED");
            task.setFailReason(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            sourceMapper.updateStatus(sourceId, 3);
            taskMapper.update(task);
        } finally {
            runningTasks.remove(taskId);
            stoppingTasks.remove(taskId);
        }
    }

    private void finishTask(CrawlTask task, String status, String reason, Long sourceId) {
        task.setStatus(status);
        task.setFailReason(reason);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.update(task);
        sourceMapper.updateStatus(sourceId, 1); // 回到规则就绪
    }

    /**
     * 批量结构化：抓取所有is_achievement=1且status=0的链接的详情页，AI结构化入库
     */
    @Async("crawlExecutor")
    public void executeBatchStructure() {
        List<AchievementLink> pendingLinks = linkMapper.selectAllPendingAchievements(500);
        if (pendingLinks.isEmpty()) return;

        log.info("开始批量结构化，待处理 {} 条", pendingLinks.size());

        int crawled = 0;
        int structured = 0;

        for (AchievementLink link : pendingLinks) {
            try {
                SourceSite source = sourceMapper.selectById(link.getSourceId());

                String html = fetchPage(link.getUrl());
                linkMapper.updateStatus(link.getId(), 1);
                crawled++;

                structureService.structureAndSave(link, source, html);
                linkMapper.updateStatus(link.getId(), 2);
                structured++;

                delay();
            } catch (Exception e) {
                log.error("结构化失败: {} - {}", link.getUrl(), e.getMessage());
                errorLogService.logError("结构化失败", link.getUrl() + ": " + e.getMessage());
                linkMapper.updateFailed(link.getId(), e.getMessage());
            }
        }

        log.info("批量结构化完成：采集 {}，结构化 {}", crawled, structured);
    }

    /**
     * AI批量判断未判断的链接标题
     */
    private void judgeLinks(Long sourceId) {
        List<AchievementLink> unjudged = linkMapper.selectUnjudgedBySource(sourceId, 500);
        if (unjudged.isEmpty()) return;

        int batchSize = 20;
        for (int i = 0; i < unjudged.size(); i += batchSize) {
            List<AchievementLink> batch = unjudged.subList(i, Math.min(i + batchSize, unjudged.size()));
            try {
                judgeBatch(batch);
            } catch (Exception e) {
                log.error("AI批量判断失败，batch offset={}", i, e);
                errorLogService.logError("AI判断失败", "batch offset=" + i + ", " + e.getMessage());                for (AchievementLink link : batch) {
                    linkMapper.updateJudgement(link.getId(), 1, "判断失败,默认通过");
                }
            }
        }
    }

    private void judgeBatch(List<AchievementLink> batch) throws Exception {
        StringBuilder titleList = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            titleList.append(String.format("%d. %s\n", i, batch.get(i).getTitle()));
        }

        String userPrompt = String.format(JUDGE_USER_PROMPT_TEMPLATE, titleList);
        String aiResponse = llmService.chat(JUDGE_SYSTEM_PROMPT, userPrompt);

        log.info("AI判断响应: {}", aiResponse);

        String jsonStr = extractJson(aiResponse);
        List<Map<String, Object>> results = objectMapper.readValue(jsonStr, new TypeReference<>() {});

        for (Map<String, Object> result : results) {
            int index = ((Number) result.get("index")).intValue();
            boolean isAchievement = (Boolean) result.get("is_achievement");
            String reason = (String) result.getOrDefault("reason", "");

            if (index >= 0 && index < batch.size()) {
                AchievementLink link = batch.get(index);
                linkMapper.updateJudgement(link.getId(), isAchievement ? 1 : 0, reason);
            }
        }
    }

    /**
     * 采集列表页，根据翻页类型分发到不同策略
     */
    private int crawlListPages(SourceSite source, CrawlRule rule, Long taskId) throws Exception {
        String paginationType = rule.getPaginationType();
        if (paginationType == null) paginationType = "URL_PATTERN";

        return switch (paginationType) {
            case "API" -> crawlListPagesApi(source, rule, taskId);
            case "BROWSER" -> crawlListPagesBrowser(source, rule, taskId);
            default -> crawlListPagesHtml(source, rule, taskId); // URL_PATTERN + CSS_SELECTOR
        };
    }

    /**
     * 传统HTML翻页：URL模板 或 CSS选择器取下一页链接
     */
    private int crawlListPagesHtml(SourceSite source, CrawlRule rule, Long taskId) throws Exception {
        String currentUrl = source.getListUrl();
        int totalFound = 0;

        // 根据 paginationType 决定翻页策略（默认 URL_PATTERN 以兼容旧数据）
        String paginationType = rule.getPaginationType();
        if (paginationType == null || paginationType.isBlank()) paginationType = "URL_PATTERN";
        boolean useUrlPattern = "URL_PATTERN".equals(paginationType)
                && rule.getUrlPattern() != null && !rule.getUrlPattern().isEmpty();
        int totalPages = 0;
        int startPage = 0;

        if (useUrlPattern) {
            // 从当前URL推断起始页码，如果无法提取则默认从1开始
            startPage = extractPageNum(currentUrl, rule.getUrlPattern());
            if (startPage <= 0) {
                startPage = 1;
            }
            // 尝试从页面获取总页数
            totalPages = maxPages;
        }

        int consecutiveFailures = 0;
        int maxConsecutiveFailures = 3; // 连续失败3页才停止

        for (int page = 0; page < maxPages && currentUrl != null; page++) {
            if (shouldStop(taskId)) break;

            log.info("采集列表页 [{}/{}]: {}", page + 1, maxPages, currentUrl);

            Document doc;
            try {
                doc = Jsoup.connect(currentUrl)
                        .userAgent(randomUA())
                        .timeout(15000)
                        .get();
                consecutiveFailures = 0; // 成功则重置连续失败计数
            } catch (Exception e) {
                consecutiveFailures++;
                log.warn("列表页抓取失败 ({}/{}): {} - {}", consecutiveFailures, maxConsecutiveFailures, currentUrl, e.getMessage());
                errorLogService.logError("列表页抓取失败", currentUrl + ": " + e.getMessage());

                if (consecutiveFailures >= maxConsecutiveFailures) {
                    log.warn("连续 {} 页抓取失败，停止翻页", maxConsecutiveFailures);
                    break;
                }

                // 跳过失败页，继续下一页
                if (useUrlPattern) {
                    currentUrl = buildNextPageUrl(rule.getUrlPattern(), currentUrl, startPage, page);
                } else {
                    currentUrl = null; // CSS选择器模式无法跳过，只能停止
                }
                if (currentUrl != null) delay();
                continue;
            }

            // 第一页时尝试探测总页数
            if (page == 0 && useUrlPattern) {
                int detected = detectTotalPages(doc);
                if (detected > 0) {
                    totalPages = Math.min(detected, maxPages);
                    log.info("  探测到总页数: {}", detected);
                }
            }

            Elements articleElements = doc.select(rule.getArticleSelector());
            int pageNewCount = 0;
            int pageMatchCount = 0;

            // 标题选择器：独立或在a标签内部
            String titleSel = rule.getTitleSelector();
            boolean useSeparateTitle = titleSel != null && !titleSel.isBlank()
                    && !titleSel.equals(rule.getArticleSelector());
            Elements titleElements = null;
            if (useSeparateTitle) {
                titleElements = doc.select(titleSel);
            }

            for (int i = 0; i < articleElements.size(); i++) {
                Element el = articleElements.get(i);
                String href = el.attr(rule.getArticleUrlAttr() != null ? rule.getArticleUrlAttr() : "href");
                if (href.isEmpty()) continue;

                String absoluteUrl = resolveUrl(currentUrl, href);
                pageMatchCount++;

                // 提取标题
                String title;
                if (useSeparateTitle && titleElements != null && i < titleElements.size()) {
                    title = titleElements.get(i).text().trim();
                } else if (useSeparateTitle) {
                    Element titleEl = el.selectFirst(titleSel);
                    title = titleEl != null ? titleEl.text().trim() : el.text().trim();
                } else {
                    title = el.text().trim();
                }

                AchievementLink link = new AchievementLink();
                link.setSourceId(source.getId());
                link.setUrl(absoluteUrl);
                link.setTitle(title.isEmpty() ? null : title);
                int inserted = linkMapper.insert(link);
                if (inserted > 0) {
                    pageNewCount++;
                }
            }

            totalFound += pageMatchCount;
            log.info("  匹配 {} 个链接，其中新链接 {} 个", pageMatchCount, pageNewCount);

            if (pageMatchCount == 0 && page > 0) {
                log.info("  页面上没有匹配到链接，停止翻页");
                break;
            }

            // 翻页：优先用URL模板，其次用CSS选择器
            if (useUrlPattern) {
                currentUrl = buildNextPageUrl(rule.getUrlPattern(), currentUrl, startPage, page);
            } else {
                currentUrl = findNextPageUrl(doc, rule, currentUrl);
            }
            if (currentUrl != null) {
                delay();
            }
        }

        return totalFound;
    }

    /**
     * 增量扫描：只看前几页
     */
    public int incrementalScan(Long sourceId, int maxScanPages) throws Exception {
        SourceSite source = sourceMapper.selectById(sourceId);
        CrawlRule rule = ruleMapper.selectBySourceId(sourceId);
        if (rule == null) return 0;

        String currentUrl = source.getListUrl();
        int totalFound = 0;
        String paginationType = rule.getPaginationType();
        if (paginationType == null || paginationType.isBlank()) paginationType = "URL_PATTERN";
        boolean useUrlPattern = "URL_PATTERN".equals(paginationType)
                && rule.getUrlPattern() != null && !rule.getUrlPattern().isEmpty();
        int startPage = useUrlPattern ? extractPageNum(currentUrl, rule.getUrlPattern()) : 0;

        for (int page = 0; page < maxScanPages && currentUrl != null; page++) {
            Document doc = Jsoup.connect(currentUrl)
                    .userAgent(randomUA())
                    .timeout(15000)
                    .get();

            Elements articleElements = doc.select(rule.getArticleSelector());
            boolean foundNew = false;

            String titleSel = rule.getTitleSelector();
            boolean useSepTitle = titleSel != null && !titleSel.isBlank()
                    && !titleSel.equals(rule.getArticleSelector());
            Elements titleEls = useSepTitle ? doc.select(titleSel) : null;

            for (int idx = 0; idx < articleElements.size(); idx++) {
                Element el = articleElements.get(idx);
                String href = el.attr(rule.getArticleUrlAttr() != null ? rule.getArticleUrlAttr() : "href");
                if (href.isEmpty()) continue;

                String absoluteUrl = resolveUrl(currentUrl, href);
                String title;
                if (useSepTitle && titleEls != null && idx < titleEls.size()) {
                    title = titleEls.get(idx).text().trim();
                } else if (useSepTitle) {
                    Element te = el.selectFirst(titleSel);
                    title = te != null ? te.text().trim() : el.text().trim();
                } else {
                    title = el.text().trim();
                }

                AchievementLink link = new AchievementLink();
                link.setSourceId(sourceId);
                link.setUrl(absoluteUrl);
                link.setTitle(title.isEmpty() ? null : title);
                int inserted = linkMapper.insert(link);
                if (inserted > 0) {
                    foundNew = true;
                    totalFound++;
                }
            }

            if (!foundNew) break;

            if (useUrlPattern) {
                currentUrl = buildNextPageUrl(rule.getUrlPattern(), currentUrl, startPage, page);
            } else {
                currentUrl = findNextPageUrl(doc, rule, currentUrl);
            }
            if (currentUrl != null) delay();
        }

        if (totalFound > 0) {
            judgeLinks(sourceId);
        }

        return totalFound;
    }

    private String findNextPageUrl(Document doc, CrawlRule rule, String currentUrl) {
        if (rule.getNextPageSelector() == null || rule.getNextPageSelector().isEmpty()) {
            return null;
        }
        Element nextPage = doc.selectFirst(rule.getNextPageSelector());
        if (nextPage == null) return null;

        String href = nextPage.attr(rule.getNextPageUrlAttr() != null ? rule.getNextPageUrlAttr() : "href");
        if (href.isEmpty() || href.equals("#")) return null;

        return resolveUrl(currentUrl, href);
    }

    /**
     * 用URL模板生成下一页URL
     * 支持模式如: /1020/list{page}.htm 或 ?page={page}
     */
    private String buildNextPageUrl(String urlPattern, String currentUrl, int startPage, int currentPageIndex) {
        // 如果urlPattern包含{page}占位符，直接用页面索引生成下一页URL
        if (urlPattern.contains("{page}")) {
            int nextNum = startPage + currentPageIndex + 1;
            String nextUrl = urlPattern.replace("{page}", String.valueOf(nextNum));
            // 如果是相对路径，拼上基础URL
            if (!nextUrl.startsWith("http")) {
                return resolveUrl(currentUrl, nextUrl);
            }
            return nextUrl;
        }

        // 无{page}占位符时，从当前URL推断下一页
        int currentPageNum = extractPageNum(currentUrl, urlPattern);
        if (currentPageNum <= 0) return null;

        // 自动探测模式：从当前URL推断下一页
        int nextNum = currentPageNum - 1;
        if (nextNum < 1) return null;

        String nextUrl = currentUrl.replaceAll("list\\d+\\.htm", "list" + nextNum + ".htm");
        if (nextUrl.equals(currentUrl)) {
            nextUrl = currentUrl.replaceAll("page=\\d+", "page=" + (currentPageNum + 1));
            if (nextUrl.equals(currentUrl)) return null;
        }
        return nextUrl;
    }

    /**
     * 从URL中提取页码数字
     */
    private int extractPageNum(String url, String urlPattern) {
        try {
            // 尝试从 listN.htm 模式提取
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("list(\\d+)\\.htm").matcher(url);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            // 尝试从 page=N 参数提取
            m = java.util.regex.Pattern.compile("[?&]page=(\\d+)").matcher(url);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            log.warn("无法从URL提取页码: {}", url);
        }
        return 0;
    }

    /**
     * 从页面分页组件中探测总页数
     */
    private int detectTotalPages(Document doc) {
        try {
            // 方式1：查找 "共X页" 类文本
            String text = doc.text();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("共\\s*(\\d+)\\s*页").matcher(text);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            // 方式2：查找分页链接中的最大数字（如 list1.htm 中的1表示最后一页）
            Elements pageLinks = doc.select("a[href*=list]");
            int maxPage = 0;
            for (Element link : pageLinks) {
                java.util.regex.Matcher pm = java.util.regex.Pattern.compile("list(\\d+)\\.htm").matcher(link.attr("href"));
                if (pm.find()) {
                    int num = Integer.parseInt(pm.group(1));
                    if (num > maxPage) maxPage = num;
                }
            }
            if (maxPage > 0) return maxPage;
        } catch (Exception e) {
            log.warn("探测总页数失败", e);
        }
        return 0;
    }

    private String fetchPage(String url) throws Exception {
        for (int retry = 0; retry < maxRetry; retry++) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(randomUA())
                        .timeout(15000)
                        .get();
                return doc.html();
            } catch (Exception e) {
                if (retry == maxRetry - 1) throw e;
                log.warn("抓取失败，重试 {}/{}: {}", retry + 1, maxRetry, url);
                Thread.sleep((long) Math.pow(2, retry) * 1000);
            }
        }
        throw new RuntimeException("抓取失败: " + url);
    }

    private String resolveUrl(String baseUrl, String href) {
        try {
            URI base = new URI(baseUrl);
            URI resolved = base.resolve(href);
            return resolved.toString();
        } catch (Exception e) {
            return href;
        }
    }

    private String randomUA() {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    private void delay() {
        try {
            Thread.sleep(requestDelayMs + random.nextInt(2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * API翻页：直接请求数据接口，解析JSON获取列表
     */
    private int crawlListPagesApi(SourceSite source, CrawlRule rule, Long taskId) throws Exception {
        if (rule.getApiUrl() == null || rule.getApiUrl().isBlank()) {
            throw new RuntimeException("API翻页模式但apiUrl为空");
        }

        int totalFound = 0;
        String pageParam = rule.getApiPageParam() != null ? rule.getApiPageParam() : "page";

        for (int page = 1; page <= maxPages; page++) {
            if (shouldStop(taskId)) break;

            // 构建API请求URL
            String apiUrl = rule.getApiUrl();
            if (apiUrl.contains("{" + pageParam + "}")) {
                apiUrl = apiUrl.replace("{" + pageParam + "}", String.valueOf(page));
            } else {
                String separator = apiUrl.contains("?") ? "&" : "?";
                apiUrl = apiUrl + separator + pageParam + "=" + page;
            }

            log.info("API翻页 [{}/{}]: {}", page, maxPages, apiUrl);

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", randomUA())
                    .build();

            String jsonBody;
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("API请求失败: {} status={}", apiUrl, response.code());
                    break;
                }
                jsonBody = response.body().string();
            }

            // 按apiDataPath解析JSON提取列表
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode listNode = navigateJsonPath(root, rule.getApiDataPath());

            if (listNode == null || !listNode.isArray() || listNode.isEmpty()) {
                log.info("API返回空列表，停止翻页");
                break;
            }

            String titleField = rule.getApiTitleField() != null ? rule.getApiTitleField() : "title";
            String urlField = rule.getApiUrlField() != null ? rule.getApiUrlField() : "url";
            int newCount = 0;

            for (JsonNode item : listNode) {
                String title = item.has(titleField) ? item.get(titleField).asText() : null;
                String url = item.has(urlField) ? item.get(urlField).asText() : null;

                if (url == null || url.isBlank()) continue;
                // API返回的URL可能是相对路径
                String absoluteUrl = url.startsWith("http") ? url : resolveUrl(source.getListUrl(), url);

                AchievementLink link = new AchievementLink();
                link.setSourceId(source.getId());
                link.setUrl(absoluteUrl);
                link.setTitle(title);
                int inserted = linkMapper.insert(link);
                if (inserted > 0) {
                    newCount++;
                    totalFound++;
                }
            }

            log.info("  API返回 {} 条，新增 {} 条", listNode.size(), newCount);
            if (newCount == 0 && page > 1) {
                log.info("  没有新数据，停止翻页");
                break;
            }

            delay();
        }

        return totalFound;
    }

    /**
     * 按路径导航JSON节点，支持嵌套如 "data.rows" 或 "data.list"
     */
    private JsonNode navigateJsonPath(JsonNode root, String path) {
        if (path == null || path.isBlank()) return root;
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null) return null;
            current = current.get(part);
        }
        return current;
    }

    /**
     * 浏览器翻页：用Playwright打开页面，点击翻页按钮
     */
    private int crawlListPagesBrowser(SourceSite source, CrawlRule rule, Long taskId) throws Exception {
        String nextBtnSelector = rule.getBrowserNextBtn();
        if (nextBtnSelector == null || nextBtnSelector.isBlank()) {
            // 没有配置浏览器翻页按钮，降级为普通HTML模式但用浏览器渲染
            log.info("BROWSER模式但未配置翻页按钮，使用浏览器渲染单页");
        }

        int totalFound = 0;
        Page page = null;

        try {
            page = playwrightService.openPage(source.getListUrl());

            for (int pageNum = 0; pageNum < maxPages; pageNum++) {
                if (shouldStop(taskId)) break;

                log.info("浏览器翻页 [{}/{}]", pageNum + 1, maxPages);

                String html = page.content();
                Document doc = playwrightService.parseToDocument(html, source.getListUrl());

                // 提取文章链接
                Elements articleElements = doc.select(rule.getArticleSelector());
                String titleSel = rule.getTitleSelector();
                boolean useSeparateTitle = titleSel != null && !titleSel.isBlank()
                        && !titleSel.equals(rule.getArticleSelector());
                Elements titleElements = useSeparateTitle ? doc.select(titleSel) : null;

                int newCount = 0;
                for (int i = 0; i < articleElements.size(); i++) {
                    Element el = articleElements.get(i);
                    String href = el.attr(rule.getArticleUrlAttr() != null ? rule.getArticleUrlAttr() : "href");
                    if (href.isEmpty()) continue;

                    String absoluteUrl = resolveUrl(source.getListUrl(), href);
                    String title;
                    if (useSeparateTitle && titleElements != null && i < titleElements.size()) {
                        title = titleElements.get(i).text().trim();
                    } else if (useSeparateTitle) {
                        Element titleEl = el.selectFirst(titleSel);
                        title = titleEl != null ? titleEl.text().trim() : el.text().trim();
                    } else {
                        title = el.text().trim();
                    }

                    AchievementLink link = new AchievementLink();
                    link.setSourceId(source.getId());
                    link.setUrl(absoluteUrl);
                    link.setTitle(title.isEmpty() ? null : title);
                    int inserted = linkMapper.insert(link);
                    if (inserted > 0) {
                        newCount++;
                        totalFound++;
                    }
                }

                log.info("  发现 {} 个新链接", newCount);

                if (newCount == 0 && pageNum > 0) {
                    log.info("  没有新链接，停止翻页");
                    break;
                }

                // 点击下一页
                if (nextBtnSelector == null || nextBtnSelector.isBlank()) {
                    break; // 没有翻页按钮，只采集当前页
                }

                String nextHtml = playwrightService.clickNextAndGetHtml(page, nextBtnSelector);
                if (nextHtml == null) {
                    log.info("  翻页按钮点击失败或不存在，停止");
                    break;
                }

                delay();
            }
        } finally {
            if (page != null) {
                try {
                    page.context().close();
                } catch (Exception e) {
                    log.warn("关闭浏览器页面失败: {}", e.getMessage());
                }
            }
        }

        return totalFound;
    }

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
