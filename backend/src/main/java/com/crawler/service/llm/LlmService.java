package com.crawler.service.llm;

/**
 * LLM服务接口 - 抽象层，方便后续切换模型
 */
public interface LlmService {

    /**
     * 发送对话请求
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return AI响应文本
     */
    String chat(String systemPrompt, String userMessage);
}
