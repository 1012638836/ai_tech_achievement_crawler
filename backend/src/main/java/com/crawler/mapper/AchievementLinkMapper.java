package com.crawler.mapper;

import com.crawler.model.entity.AchievementLink;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AchievementLinkMapper {

    @Insert("INSERT IGNORE INTO achievement_link(source_id, url, title) VALUES(#{sourceId}, #{url}, #{title})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AchievementLink link);

    @Select("SELECT * FROM achievement_link WHERE source_id = #{sourceId} AND status = 0 AND is_achievement = 1 LIMIT #{limit}")
    List<AchievementLink> selectPendingBySource(@Param("sourceId") Long sourceId, @Param("limit") int limit);

    @Select("SELECT * FROM achievement_link WHERE source_id = #{sourceId} AND is_achievement IS NULL LIMIT #{limit}")
    List<AchievementLink> selectUnjudgedBySource(@Param("sourceId") Long sourceId, @Param("limit") int limit);

    @Update("UPDATE achievement_link SET is_achievement = #{isAchievement}, judge_reason = #{judgeReason} WHERE id = #{id}")
    int updateJudgement(@Param("id") Long id, @Param("isAchievement") Integer isAchievement, @Param("judgeReason") String judgeReason);

    @Update("UPDATE achievement_link SET status = #{status}, crawled_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE achievement_link SET status = 3, fail_reason = LEFT(#{failReason}, 500) WHERE id = #{id}")
    int updateFailed(@Param("id") Long id, @Param("failReason") String failReason);

    @Select("SELECT COUNT(*) FROM achievement_link WHERE source_id = #{sourceId}")
    long countBySource(Long sourceId);

    @Select("SELECT COUNT(*) FROM achievement_link WHERE url = #{url}")
    int existsByUrl(String url);

    @Select("SELECT COUNT(*) FROM achievement_link WHERE is_achievement = 1 AND status = 0")
    long countPendingAchievements();

    @Select("SELECT * FROM achievement_link WHERE is_achievement = 1 AND status = 0 LIMIT #{limit}")
    List<AchievementLink> selectAllPendingAchievements(int limit);

    List<AchievementLink> selectPage(@Param("sourceId") Long sourceId,
                                     @Param("isAchievement") Integer isAchievement,
                                     @Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    long countPageTotal(@Param("sourceId") Long sourceId,
                        @Param("isAchievement") Integer isAchievement,
                        @Param("keyword") String keyword);
}
