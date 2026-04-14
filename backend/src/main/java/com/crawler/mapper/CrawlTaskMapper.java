package com.crawler.mapper;

import com.crawler.model.entity.CrawlTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CrawlTaskMapper {

    @Insert("INSERT INTO crawl_task(source_id, type, status) VALUES(#{sourceId}, #{type}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CrawlTask task);

    @Select("SELECT * FROM crawl_task WHERE id = #{id}")
    CrawlTask selectById(Long id);

    @Select("SELECT * FROM crawl_task ORDER BY id DESC LIMIT #{offset}, #{size}")
    List<CrawlTask> selectPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM crawl_task")
    long countAll();

    @Update("UPDATE crawl_task SET status=#{status}, total_found=#{totalFound}, total_crawled=#{totalCrawled}, " +
            "total_structured=#{totalStructured}, started_at=#{startedAt}, finished_at=#{finishedAt}, " +
            "fail_reason=LEFT(#{failReason}, 1000) WHERE id=#{id}")
    int update(CrawlTask task);

    @Delete("DELETE FROM crawl_task WHERE id = #{id}")
    int deleteById(Long id);
}
