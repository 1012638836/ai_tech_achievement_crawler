package com.crawler.mapper;

import com.crawler.model.entity.ErrorLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ErrorLogMapper {

    @Insert("INSERT INTO error_log(error_type, error_detail) VALUES(#{errorType}, #{errorDetail})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ErrorLog log);

    @Select("SELECT * FROM error_log ORDER BY created_time DESC LIMIT #{size} OFFSET #{offset}")
    List<ErrorLog> selectPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM error_log")
    long countAll();
}
