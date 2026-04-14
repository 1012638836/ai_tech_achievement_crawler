package com.crawler.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ErrorLog {
    private Long id;
    private String errorType;
    private String errorDetail;
    private LocalDateTime createdTime;
}
