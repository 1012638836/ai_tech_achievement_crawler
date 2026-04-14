package com.crawler.mapper;

import com.crawler.model.entity.CrawlRule;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CrawlRuleMapper {

    @Insert("INSERT INTO crawl_rule(source_id, article_selector, article_url_attr, title_selector, " +
            "next_page_selector, next_page_url_attr, content_selector, url_pattern, " +
            "pagination_type, api_url, api_method, api_page_param, api_data_path, " +
            "api_title_field, api_url_field, browser_next_btn, ai_raw_response) " +
            "VALUES(#{sourceId}, #{articleSelector}, #{articleUrlAttr}, #{titleSelector}, " +
            "#{nextPageSelector}, #{nextPageUrlAttr}, #{contentSelector}, #{urlPattern}, " +
            "#{paginationType}, #{apiUrl}, #{apiMethod}, #{apiPageParam}, #{apiDataPath}, " +
            "#{apiTitleField}, #{apiUrlField}, #{browserNextBtn}, #{aiRawResponse})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CrawlRule rule);

    @Select("SELECT * FROM crawl_rule WHERE source_id = #{sourceId} ORDER BY id DESC LIMIT 1")
    CrawlRule selectBySourceId(Long sourceId);

    @Update("UPDATE crawl_rule SET article_selector=#{articleSelector}, article_url_attr=#{articleUrlAttr}, " +
            "title_selector=#{titleSelector}, " +
            "next_page_selector=#{nextPageSelector}, next_page_url_attr=#{nextPageUrlAttr}, " +
            "content_selector=#{contentSelector}, url_pattern=#{urlPattern}, " +
            "pagination_type=#{paginationType}, api_url=#{apiUrl}, api_method=#{apiMethod}, " +
            "api_page_param=#{apiPageParam}, api_data_path=#{apiDataPath}, " +
            "api_title_field=#{apiTitleField}, api_url_field=#{apiUrlField}, " +
            "browser_next_btn=#{browserNextBtn}, verified=#{verified} WHERE id=#{id}")
    int update(CrawlRule rule);

    @Delete("DELETE FROM crawl_rule WHERE source_id = #{sourceId}")
    int deleteBySourceId(Long sourceId);
}
