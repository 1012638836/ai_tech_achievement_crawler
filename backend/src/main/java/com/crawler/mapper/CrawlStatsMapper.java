package com.crawler.mapper;

import com.crawler.model.entity.CrawlStats;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface CrawlStatsMapper {

    @Insert("INSERT INTO crawl_stats(stat_date, source_id, new_links, new_achievements) " +
            "VALUES(#{statDate}, #{sourceId}, #{newLinks}, #{newAchievements}) " +
            "ON DUPLICATE KEY UPDATE new_links = new_links + #{newLinks}, new_achievements = new_achievements + #{newAchievements}")
    int upsert(CrawlStats stats);

    @Select("SELECT * FROM crawl_stats WHERE source_id IS NULL AND stat_date >= #{startDate} ORDER BY stat_date")
    List<CrawlStats> selectGlobalTrend(LocalDate startDate);

    @Select("SELECT s.school, SUM(cs.new_achievements) as total " +
            "FROM crawl_stats cs JOIN source_site s ON cs.source_id = s.id " +
            "WHERE cs.stat_date >= #{startDate} " +
            "GROUP BY cs.source_id, s.school ORDER BY total DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopSources(@Param("startDate") LocalDate startDate, @Param("limit") int limit);

    /**
     * 从 achievement_link 和 achievement 实时聚合趋势数据
     */
    @Select("SELECT d.stat_date as statDate, " +
            "IFNULL(l.cnt, 0) as newLinks, " +
            "IFNULL(a.cnt, 0) as newAchievements " +
            "FROM ( " +
            "  SELECT DATE(#{startDate} + INTERVAL seq DAY) as stat_date " +
            "  FROM ( " +
            "    SELECT @rownum := @rownum + 1 as seq " +
            "    FROM information_schema.COLUMNS, (SELECT @rownum := -1) r " +
            "    LIMIT 31 " +
            "  ) nums " +
            "  WHERE DATE(#{startDate} + INTERVAL seq DAY) <= CURDATE() " +
            ") d " +
            "LEFT JOIN ( " +
            "  SELECT DATE(discovered_at) as dt, COUNT(*) as cnt " +
            "  FROM achievement_link WHERE discovered_at >= #{startDate} GROUP BY DATE(discovered_at) " +
            ") l ON d.stat_date = l.dt " +
            "LEFT JOIN ( " +
            "  SELECT DATE(created_at) as dt, COUNT(*) as cnt " +
            "  FROM achievement WHERE created_at >= #{startDate} GROUP BY DATE(created_at) " +
            ") a ON d.stat_date = a.dt " +
            "ORDER BY d.stat_date")
    List<Map<String, Object>> selectTrendFromReal(@Param("startDate") LocalDate startDate);

    /**
     * 从 achievement 实时聚合 TOP 数据源
     */
    @Select("SELECT s.school, COUNT(a.id) as total " +
            "FROM achievement a JOIN source_site s ON a.source_id = s.id " +
            "WHERE a.created_at >= #{startDate} " +
            "GROUP BY a.source_id, s.school ORDER BY total DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopSourcesFromReal(@Param("startDate") LocalDate startDate, @Param("limit") int limit);
}
