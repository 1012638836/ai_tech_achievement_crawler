package com.crawler.mapper;

import com.crawler.model.entity.AchievementResearcher;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AchievementResearcherMapper {

    @Insert("INSERT INTO achievement_researcher(achievement_id, name, affiliation, school, college) " +
            "VALUES(#{achievementId}, #{name}, #{affiliation}, #{school}, #{college})")
    int insert(AchievementResearcher researcher);

    @Select("SELECT * FROM achievement_researcher WHERE achievement_id = #{achievementId}")
    List<AchievementResearcher> selectByAchievementId(Long achievementId);

    @Select("SELECT * FROM achievement_researcher")
    List<AchievementResearcher> selectAll();

    @Update("UPDATE achievement_researcher SET school=#{school}, college=#{college} WHERE id=#{id}")
    int updateSchoolCollege(AchievementResearcher researcher);
}
