package com.crawler.service;

import com.microsoft.playwright.*;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightService {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightService.class);

    @Value("${crawler.browser-timeout-ms:30000}")
    private int browserTimeout;

    private Playwright playwright;
    private Browser browser;

    /**
     * 懒加载初始化浏览器
     */
    private synchronized Browser getBrowser() {
        if (browser == null || !browser.isConnected()) {
            if (playwright == null) {
                playwright = Playwright.create();
            }
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            log.info("Playwright浏览器已启动");
        }
        return browser;
    }

    /**
     * 用浏览器打开页面，等JS渲染完成后返回完整HTML
     */
    public String fetchPageWithBrowser(String url) {
        BrowserContext context = getBrowser().newContext(
                new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        );
        try {
            Page page = context.newPage();
            page.setDefaultTimeout(browserTimeout);
            page.navigate(url);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            return page.content();
        } finally {
            context.close();
        }
    }

    /**
     * 用浏览器打开页面，提取当前页内容后点击翻页按钮，返回下一页HTML
     * @return 下一页HTML，如果按钮不存在或点击失败返回null
     */
    public String clickNextAndGetHtml(Page page, String nextBtnSelector) {
        try {
            Locator btn = page.locator(nextBtnSelector);
            if (btn.count() == 0) {
                log.info("翻页按钮不存在: {}", nextBtnSelector);
                return null;
            }
            btn.first().click();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            // 等待一小段时间确保内容更新
            page.waitForTimeout(1000);
            return page.content();
        } catch (Exception e) {
            log.warn("点击翻页按钮失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 创建一个新的浏览器页面（用于多页翻页场景，复用同一个page对象）
     */
    public Page openPage(String url) {
        BrowserContext context = getBrowser().newContext(
                new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        );
        Page page = context.newPage();
        page.setDefaultTimeout(browserTimeout);
        page.navigate(url);
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        return page;
    }

    /**
     * 将浏览器渲染后的HTML转为Jsoup Document
     */
    public Document parseToDocument(String html, String baseUrl) {
        return Jsoup.parse(html, baseUrl);
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
            log.info("Playwright浏览器已关闭");
        } catch (Exception e) {
            log.warn("关闭Playwright失败: {}", e.getMessage());
        }
    }
}
