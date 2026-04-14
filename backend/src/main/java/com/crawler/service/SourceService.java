package com.crawler.service;

import com.crawler.mapper.SourceSiteMapper;
import com.crawler.model.dto.PageResult;
import com.crawler.model.entity.SourceSite;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);
    private final SourceSiteMapper sourceMapper;

    public SourceService(SourceSiteMapper sourceMapper) {
        this.sourceMapper = sourceMapper;
    }

    /**
     * Excel导入数据源
     * Excel格式：学校/机构 | 列表页URL | 备注(可选)
     */
    public int importFromExcel(MultipartFile file) throws Exception {
        List<SourceSite> sites = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int startRow = 1; // 跳过表头

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String school = getCellString(row.getCell(0));
                String listUrl = getCellString(row.getCell(1));
                String remark = getCellString(row.getCell(2));

                if (school.isEmpty() || listUrl.isEmpty()) continue;

                SourceSite site = new SourceSite();
                site.setSchool(school.trim());
                site.setListUrl(listUrl.trim());
                site.setRemark(remark.trim());
                sites.add(site);
            }
        }

        // 查询数据库中已有的URL，用于去重
        Set<String> existingUrls = new HashSet<>(sourceMapper.selectAllListUrls());

        int count = 0;
        int skipped = 0;
        for (SourceSite site : sites) {
            if (existingUrls.contains(site.getListUrl())) {
                skipped++;
                log.info("跳过重复URL: {} - {}", site.getSchool(), site.getListUrl());
                continue;
            }
            try {
                sourceMapper.insert(site);
                existingUrls.add(site.getListUrl());
                count++;
            } catch (Exception e) {
                log.warn("导入数据源失败: {} - {}", site.getListUrl(), e.getMessage());
            }
        }
        if (skipped > 0) {
            log.info("本次导入跳过 {} 条重复URL", skipped);
        }
        return count;
    }

    public PageResult<SourceSite> listPage(String school, Integer status, int page, int size) {
        int offset = (page - 1) * size;
        List<SourceSite> list = sourceMapper.selectPage(school, status, offset, size);
        long total = sourceMapper.countTotal(school, status);
        return PageResult.of(list, total, page, size);
    }

    public SourceSite getById(Long id) {
        return sourceMapper.selectById(id);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue();
    }
}
