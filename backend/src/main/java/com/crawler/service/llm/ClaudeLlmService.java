package com.crawler.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class ClaudeLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    @Value("${claude.api-key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    @Value("${claude.base-url}")
    private String baseUrl;

    public ClaudeLlmService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private boolean isOpenAiCompatible() {
        return !model.startsWith("claude");
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (isOpenAiCompatible()) {
            return chatOpenAi(systemPrompt, userMessage);
        } else {
            return chatClaude(systemPrompt, userMessage);
        }
    }

    private String chatOpenAi(String systemPrompt, String userMessage) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 4096);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            String url = baseUrl.replace("/v1/messages", "/v1/chat/completions");

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON_TYPE))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    log.error("OpenAI API error: {} - {}", response.code(), errorBody);
                    throw new RuntimeException("LLM API调用失败: " + response.code());
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    return choices.get(0).get("message").get("content").asText();
                }
                throw new RuntimeException("LLM API返回格式异常");
            }
        } catch (IOException e) {
            log.error("LLM API调用异常", e);
            throw new RuntimeException("LLM API调用异常: " + e.getMessage(), e);
        }
    }

    private String chatClaude(String systemPrompt, String userMessage) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 4096);
            requestBody.put("system", systemPrompt);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", userMessage);

            Request request = new Request.Builder()
                    .url(baseUrl)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON_TYPE))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    log.error("Claude API error: {} - {}", response.code(), errorBody);
                    throw new RuntimeException("Claude API调用失败: " + response.code());
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode contentArray = root.get("content");
                if (contentArray != null && contentArray.isArray() && !contentArray.isEmpty()) {
                    return contentArray.get(0).get("text").asText();
                }
                throw new RuntimeException("Claude API返回格式异常");
            }
        } catch (IOException e) {
            log.error("Claude API调用异常", e);
            throw new RuntimeException("Claude API调用异常: " + e.getMessage(), e);
        }
    }
}
