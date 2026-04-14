package com.crawler.mapper;

import com.crawler.model.entity.SourceSite;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SourceSiteMapper {

    @Insert("INSERT IGNORE INTO source_site(school, list_url, remark) VALUES(#{school}, #{listUrl}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SourceSite site);

    @Select("SELECT * FROM source_site WHERE id = #{id}")
    SourceSite selectById(Long id);

    List<SourceSite> selectPage(@Param("school") String school,
                                @Param("status") Integer status,
                                @Param("offset") int offset,
                                @Param("size") int size);

    long countTotal(@Param("school") String school, @Param("status") Integer status);

    @Update("UPDATE source_site SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE source_site SET last_crawl_time = NOW(), total_crawled = #{totalCrawled} WHERE id = #{id}")
    int updateCrawlInfo(@Param("id") Long id, @Param("totalCrawled") Integer totalCrawled);

    @Select("SELECT * FROM source_site WHERE status = 1")
    List<SourceSite> selectReadySources();

    @Select("SELECT COUNT(*) FROM source_site")
    long countAll();

    @Select("SELECT list_url FROM source_site")
    List<String> selectAllListUrls();

    @Delete("DELETE FROM source_site WHERE id = #{id}")
    int deleteById(Long id);
}
