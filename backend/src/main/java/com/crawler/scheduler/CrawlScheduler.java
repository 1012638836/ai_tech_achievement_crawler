package com.crawler.scheduler;

import com.crawler.mapper.AchievementLinkMapper;
import com.crawler.mapper.CrawlStatsMapper;
import com.crawler.mapper.SourceSiteMapper;
import com.crawler.model.entity.CrawlStats;
import com.crawler.model.entity.SourceSite;
import com.crawler.service.CrawlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final SourceSiteMapper sourceMapper;
    private final CrawlService crawlService;
    private final CrawlStatsMapper statsMapper;

    public CrawlScheduler(SourceSiteMapper sourceMapper, CrawlService crawlService,
                          CrawlStatsMapper statsMapper) {
        this.sourceMapper = sourceMapper;
        this.crawlService = crawlService;
        this.statsMapper = statsMapper;
    }

    /**
     * 每天凌晨2点执行增量扫描
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyIncrementalScan() {
        log.info("开始每日增量扫描...");
        List<SourceSite> sources = sourceMapper.selectReadySources();

        for (SourceSite source : sources) {
            try {
                int found = crawlService.incrementalScan(source.getId(), 3);
                log.info("增量扫描完成: {} - 发现{}个新链接", source.getSchool(), found);

                // 记录统计
                if (found > 0) {
                    CrawlStats stats = new CrawlStats();
                    stats.setStatDate(LocalDate.now());
                    stats.setSourceId(source.getId());
                    stats.setNewLinks(found);
                    stats.setNewAchievements(0);
                    statsMapper.upsert(stats);
                }
            } catch (Exception e) {
                log.error("增量扫描失败: {} - {}", source.getSchool(), e.getMessage());
            }
        }

        log.info("每日增量扫描完成");
    }
}
