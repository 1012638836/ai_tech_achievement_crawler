package com.crawler.mapper;

import com.crawler.model.entity.Achievement;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AchievementMapper {

    int insert(Achievement achievement);

    @Select("SELECT * FROM achievement WHERE id = #{id}")
    Achievement selectById(Long id);

    List<Achievement> selectPage(@Param("school") String school,
                                 @Param("domain") String domain,
                                 @Param("stage") String stage,
                                 @Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countTotal(@Param("school") String school,
                    @Param("domain") String domain,
                    @Param("stage") String stage,
                    @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM achievement")
    long countAll();

    @Select("SELECT COUNT(*) FROM achievement WHERE DATE(created_at) = CURDATE()")
    long countToday();

    @Delete("DELETE FROM achievement_researcher WHERE achievement_id = #{id}")
    int deleteResearchersById(Long id);

    @Delete("DELETE FROM achievement WHERE id = #{id}")
    int deleteById(Long id);
}
