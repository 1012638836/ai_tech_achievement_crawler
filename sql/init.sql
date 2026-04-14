-- 科技成果采集系统 建表SQL
-- MySQL 8.x

CREATE DATABASE IF NOT EXISTS tech_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tech_crawler;

-- 1. 数据源
CREATE TABLE source_site (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  school VARCHAR(100) NOT NULL COMMENT '学校/机构名称',
  list_url VARCHAR(500) NOT NULL COMMENT '列表页URL',
  status TINYINT DEFAULT 0 COMMENT '0-待分析 1-规则就绪 2-采集中 3-异常',
  last_crawl_time DATETIME COMMENT '最后采集时间',
  total_crawled INT DEFAULT 0 COMMENT '已采集成果数',
  remark VARCHAR(500) COMMENT '备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '数据源';

-- 2. 采集规则（AI生成）
CREATE TABLE crawl_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL COMMENT '关联数据源',
  article_selector VARCHAR(500) COMMENT '文章链接CSS选择器',
  article_url_attr VARCHAR(50) DEFAULT 'href' COMMENT '链接属性',
  title_selector VARCHAR(500) COMMENT '标题CSS选择器(与链接选择器独立)',
  next_page_selector VARCHAR(500) COMMENT '下一页CSS选择器',
  next_page_url_attr VARCHAR(50) DEFAULT 'href',
  content_selector VARCHAR(500) COMMENT '详情页正文CSS选择器',
  url_pattern VARCHAR(500) COMMENT '翻页URL模板',
  pagination_type VARCHAR(20) DEFAULT 'URL_PATTERN' COMMENT '翻页类型: URL_PATTERN/CSS_SELECTOR/API/BROWSER',
  api_url VARCHAR(500) COMMENT 'API翻页地址',
  api_method VARCHAR(10) COMMENT 'API请求方法 GET/POST',
  api_page_param VARCHAR(50) COMMENT 'API页码参数名',
  api_data_path VARCHAR(200) COMMENT 'API返回数据路径',
  api_title_field VARCHAR(50) COMMENT 'API标题字段名',
  api_url_field VARCHAR(50) COMMENT 'API链接字段名',
  browser_next_btn VARCHAR(500) COMMENT '浏览器点击翻页按钮选择器',
  ai_raw_response TEXT COMMENT 'AI分析原始响应',
  verified TINYINT DEFAULT 0 COMMENT '0-未验证 1-已验证可用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (source_id) REFERENCES source_site(id)
) COMMENT '采集规则';

-- 3. 详情页链接
CREATE TABLE achievement_link (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  url VARCHAR(500) NOT NULL COMMENT '详情页URL',
  title VARCHAR(500) COMMENT '列表页标题',
  is_achievement TINYINT DEFAULT NULL COMMENT 'AI判断: null-未判断 1-是科研成果 0-不是',
  judge_reason VARCHAR(200) COMMENT 'AI判断理由',
  status TINYINT DEFAULT 0 COMMENT '0-待采集 1-已采集 2-已结构化 3-失败',
  fail_reason TEXT,
  discovered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  crawled_at DATETIME,
  UNIQUE KEY uk_url (url(255)),
  FOREIGN KEY (source_id) REFERENCES source_site(id)
) COMMENT '采集到的详情页链接';

-- 4. 结构化成果
CREATE TABLE achievement (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  link_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  title VARCHAR(500) NOT NULL COMMENT '标题',
  content LONGTEXT COMMENT '内容(带HTML结构和图片URL)',
  url VARCHAR(500) NOT NULL COMMENT '详情页链接',
  school VARCHAR(100) COMMENT '学校/机构',
  field VARCHAR(200) COMMENT '研究领域',
  journals VARCHAR(500) COMMENT '发表期刊(分号分隔)',
  funders VARCHAR(500) COMMENT '资助方(分号分隔)',
  publish_time DATETIME COMMENT '发表时间',
  tech_keywords VARCHAR(1000) COMMENT '技术关键词(逗号分隔)',
  stage VARCHAR(20) COMMENT '所处阶段',
  domain VARCHAR(50) COMMENT '所处领域',
  application_scenario VARCHAR(500) COMMENT '应用场景',
  ai_raw_response TEXT COMMENT 'AI原始响应',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (link_id) REFERENCES achievement_link(id),
  FOREIGN KEY (source_id) REFERENCES source_site(id)
) COMMENT '结构化科技成果';

-- 5. 研究人员
CREATE TABLE achievement_researcher (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  achievement_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL COMMENT '姓名',
  affiliation VARCHAR(200) COMMENT '单位/院系',
  school VARCHAR(100) COMMENT '大学/机构名称',
  college VARCHAR(100) COMMENT '学院/系所名称',
  FOREIGN KEY (achievement_id) REFERENCES achievement(id)
) COMMENT '研究人员';

-- 6. 采集任务
CREATE TABLE crawl_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT COMMENT '关联数据源(null=全量扫描)',
  type VARCHAR(20) NOT NULL COMMENT 'FULL/INCREMENTAL/RULE_GEN',
  status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  total_found INT DEFAULT 0 COMMENT '发现链接数',
  total_crawled INT DEFAULT 0 COMMENT '采集成功数',
  total_structured INT DEFAULT 0 COMMENT '结构化成功数',
  fail_reason VARCHAR(1000),
  started_at DATETIME,
  finished_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '采集任务';

-- 7. 每日统计
CREATE TABLE crawl_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date DATE NOT NULL COMMENT '统计日期',
  source_id BIGINT COMMENT 'null=全局统计',
  new_links INT DEFAULT 0 COMMENT '新发现链接数',
  new_achievements INT DEFAULT 0 COMMENT '新结构化成果数',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_date_source (stat_date, source_id)
) COMMENT '每日统计快照';

-- 8. 错误日志
CREATE TABLE error_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  error_type VARCHAR(50) NOT NULL COMMENT '错误类型',
  error_detail TEXT COMMENT '具体错误信息',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '错误发生时间'
) COMMENT '系统错误日志';
