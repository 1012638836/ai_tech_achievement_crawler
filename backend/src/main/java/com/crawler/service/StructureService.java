package com.crawler.service;

import com.crawler.mapper.AchievementMapper;
import com.crawler.mapper.AchievementResearcherMapper;
import com.crawler.model.dto.AchievementStructuredDTO;
import com.crawler.model.entity.Achievement;
import com.crawler.model.entity.AchievementLink;
import com.crawler.model.entity.AchievementResearcher;
import com.crawler.model.entity.SourceSite;
import com.crawler.service.llm.LlmService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class StructureService {

    private static final Logger log = LoggerFactory.getLogger(StructureService.class);

    private final LlmService llmService;
    private final AchievementMapper achievementMapper;
    private final AchievementResearcherMapper researcherMapper;
    private final ObjectMapper objectMapper;
    private final ErrorLogService errorLogService;

    private static final String SYSTEM_PROMPT = """
            你是一个科技成果信息提取专家。从给定的科技成果网页内容中提取结构化信息。
            严格按JSON格式输出，不要输出任何其他内容。
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            请从以下科技成果网页内容中提取信息，返回JSON格式：
            {
              "title": "成果标题",
              "content": "正文内容(保留HTML结构和图片URL)",
              "field": "研究领域",
              "researchers": [{"name": "姓名", "school": "大学/机构名称", "college": "学院/系所名称"}],
              "journals": "发表期刊(多个用分号分隔)",
              "funders": "资助方(多个用分号分隔)",
              "publishTime": "发表时间(格式yyyy-MM-dd HH:mm)",
              "techKeywords": "技术关键词(仅专有名词,逗号分隔)",
              "stage": "所处阶段",
              "domain": "所处领域",
              "applicationScenario": "应用场景(一句话关键词短语)"
            }

            字段说明：
            - researchers 重要规则：
              1. school 填写标准化的大学/研究机构全称（如"南开大学""中国科学院"），不含学院部分
              2. college 填写学院/系所/研究所名称（如"物理科学学院""化学研究所"）
              3. 如果一个研究人员属于多个机构，只取第一个机构
              4. 标准化示例："南开"→"南开大学"，"北大"→"北京大学"，"中科院"→"中国科学院"
              5. 海外机构也拆分：school="麻省理工学院", college="电气工程与计算机科学系"
            - techKeywords：只提取在原文中完整出现过的专有名词（如"极化激元""蛋白酶体""固态电解质"），严禁自行概括、改写或添加原文中未出现的词汇
            - stage 从以下枚举选择：科研进展、概念前、概念、小试、中试、产业化、其他。无法判断默认"科研进展"
            - domain 从以下枚举选择最匹配的一个：新一代信息技术、医药健康、集成电路、智能网联汽车、智能制造与装备、绿色能源与节能环保、区块链与先进计算、科技服务业、智慧城市、信息内容消费、软件信息服务、新材料
            - publishTime：格式 yyyy-MM-dd HH:mm，无法精确到分钟则用 00:00
            - applicationScenario：用关键词短语描述可能的应用场景（如"红外光谱检测，生物传感器，热辐射调控"）

            网页内容：
            ---
            %s
            ---
            """;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public StructureService(LlmService llmService, AchievementMapper achievementMapper,
                            AchievementResearcherMapper researcherMapper, ObjectMapper objectMapper,
                            ErrorLogService errorLogService) {
        this.llmService = llmService;
        this.achievementMapper = achievementMapper;
        this.researcherMapper = researcherMapper;
        this.objectMapper = objectMapper;
        this.errorLogService = errorLogService;
    }

    /**
     * 对详情页HTML进行AI结构化并存入数据库
     */
    public Achievement structureAndSave(AchievementLink link, SourceSite source, String html) throws Exception {
        // 截断过长HTML
        String truncatedHtml = html.length() > 20000 ? html.substring(0, 20000) : html;

        String userPrompt = String.format(USER_PROMPT_TEMPLATE, truncatedHtml);
        String aiResponse = llmService.chat(SYSTEM_PROMPT, userPrompt);

        log.info("AI结构化响应 [{}]: {}...", link.getUrl(),
                aiResponse.length() > 200 ? aiResponse.substring(0, 200) : aiResponse);

        // 解析JSON
        String jsonStr = extractJson(aiResponse);
        AchievementStructuredDTO dto = objectMapper.readValue(jsonStr, AchievementStructuredDTO.class);

        // 存入achievement表
        Achievement achievement = new Achievement();
        achievement.setLinkId(link.getId());
        achievement.setSourceId(source.getId());
        achievement.setTitle(dto.getTitle());
        // 将内容中的相对路径图片URL转为绝对路径
        String content = dto.getContent();
        if (content != null && link.getUrl() != null) {
            content = fixRelativeUrls(content, link.getUrl());
        }
        achievement.setContent(content);
        achievement.setUrl(link.getUrl());
        achievement.setSchool(source.getSchool());
        achievement.setField(dto.getField());
        achievement.setJournals(dto.getJournals());
        achievement.setFunders(dto.getFunders());
        achievement.setTechKeywords(dto.getTechKeywords());
        achievement.setStage(dto.getStage());
        achievement.setDomain(dto.getDomain());
        achievement.setApplicationScenario(dto.getApplicationScenario());
        achievement.setAiRawResponse(aiResponse);

        // 解析发表时间
        if (dto.getPublishTime() != null && !dto.getPublishTime().isEmpty()) {
            try {
                achievement.setPublishTime(LocalDateTime.parse(dto.getPublishTime(), DTF));
            } catch (Exception e) {
                log.warn("发表时间解析失败: {}", dto.getPublishTime());
            }
        }

        achievementMapper.insert(achievement);

        // 存入研究人员
        List<AchievementStructuredDTO.ResearcherDTO> researchers = dto.getResearchers();
        if (researchers != null) {
            for (AchievementStructuredDTO.ResearcherDTO r : researchers) {
                AchievementResearcher researcher = new AchievementResearcher();
                researcher.setAchievementId(achievement.getId());
                researcher.setName(r.getName());
                researcher.setAffiliation(r.getAffiliation());
                researcher.setSchool(r.getSchool());
                researcher.setCollege(r.getCollege());
                researcherMapper.insert(researcher);
            }
        }

        return achievement;
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

    /**
     * 将HTML内容中的相对路径(src/href)转为绝对路径
     */
    private String fixRelativeUrls(String html, String pageUrl) {
        try {
            java.net.URI base = new java.net.URI(pageUrl);
            String baseStr = base.getScheme() + "://" + base.getHost();
            // 替换 src="/ 开头的相对路径
            html = html.replaceAll("src=\"/", "src=\"" + baseStr + "/");
            html = html.replaceAll("src='/", "src='" + baseStr + "/");
        } catch (Exception e) {
            log.warn("修正相对路径失败: {}", e.getMessage());
        }
        return html;
    }

    private static final String FIX_AFFILIATION_SYSTEM = """
            你是一个机构名称标准化专家。将研究人员的机构名称拆分为"大学/机构"和"学院/系所"两部分，并标准化名称。
            严格按JSON数组格式输出，不要输出其他内容。
            """;

    private static final String FIX_AFFILIATION_USER = """
            将以下研究人员的affiliation拆分为school(大学/机构)和college(学院/系所)，返回JSON数组：
            [{"id": 原始id, "school": "标准化大学名", "college": "学院名"}]

            规则：
            1. school填写标准化全称：如"南开"→"南开大学"，"北大"→"北京大学"，"中科院"→"中国科学院"
            2. college填写学院/研究所/系：如"物理科学学院""化学研究所"
            3. 如果affiliation只有大学名没有学院，college留空
            4. 如果affiliation为空，school和college都留空
            5. 多个机构只取第一个
            6. 海外机构也标准化拆分

            数据：
            %s
            """;

    /**
     * 修正存量数据：用AI将affiliation拆分为school+college
     */
    @Async("crawlExecutor")
    public void fixExistingAffiliations() {
        List<AchievementResearcher> all = researcherMapper.selectAll();
        // 只处理school为空的
        List<AchievementResearcher> needFix = all.stream()
                .filter(r -> r.getSchool() == null || r.getSchool().isEmpty())
                .toList();

        if (needFix.isEmpty()) {
            log.info("没有需要修正的研究人员数据");
            return;
        }

        log.info("开始修正研究人员机构数据，共 {} 条", needFix.size());

        int batchSize = 30;
        int fixed = 0;
        for (int i = 0; i < needFix.size(); i += batchSize) {
            List<AchievementResearcher> batch = needFix.subList(i, Math.min(i + batchSize, needFix.size()));
            try {
                StringBuilder data = new StringBuilder();
                for (AchievementResearcher r : batch) {
                    data.append(String.format("{\"id\":%d, \"name\":\"%s\", \"affiliation\":\"%s\"}\n",
                            r.getId(), r.getName(), r.getAffiliation() != null ? r.getAffiliation() : ""));
                }

                String userPrompt = String.format(FIX_AFFILIATION_USER, data);
                String aiResponse = llmService.chat(FIX_AFFILIATION_SYSTEM, userPrompt);
                String jsonStr = extractJson(aiResponse);

                List<Map<String, Object>> results = objectMapper.readValue(jsonStr, new TypeReference<>() {});

                for (Map<String, Object> result : results) {
                    Long id = ((Number) result.get("id")).longValue();
                    String school = (String) result.getOrDefault("school", "");
                    String college = (String) result.getOrDefault("college", "");

                    AchievementResearcher update = new AchievementResearcher();
                    update.setId(id);
                    update.setSchool(school);
                    update.setCollege(college);
                    researcherMapper.updateSchoolCollege(update);
                    fixed++;
                }

                log.info("已修正 {}/{} 条", fixed, needFix.size());
            } catch (Exception e) {
                log.error("修正机构数据失败, batch offset={}", i, e);
                errorLogService.logError("机构修正失败", "batch offset=" + i + ", " + e.getMessage());
            }
        }

        log.info("机构数据修正完成，共修正 {} 条", fixed);
    }
}
