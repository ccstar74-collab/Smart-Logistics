package com.smartlogistics.agent;

import java.nio.file.Path;
import java.nio.file.Paths;

final class AppConfig {
    final int port;
    final Path knowledgeDirectory;
    final String modelApiStyle;
    final String modelBaseUrl;
    final String modelApiKey;
    final String modelName;
    final String adminToken;
    final String businessApiBaseUrl;
    final String businessApiToken;
    final String businessApiUsername;
    final String businessApiPassword;
    final int businessApiTimeoutMillis;
    final int topK;
    final int maxQuestionLength;

    AppConfig(int port, Path knowledgeDirectory,
              String modelApiStyle, String modelBaseUrl,
              String modelApiKey, String modelName, String adminToken,
              int topK, int maxQuestionLength) {
        this(port, knowledgeDirectory, modelApiStyle, modelBaseUrl,
                modelApiKey, modelName, adminToken, "", "", "", "", 5000, topK, maxQuestionLength);
    }

    AppConfig(int port, Path knowledgeDirectory,
              String modelApiStyle, String modelBaseUrl,
              String modelApiKey, String modelName, String adminToken,
              String businessApiBaseUrl, String businessApiToken, int businessApiTimeoutMillis,
              int topK, int maxQuestionLength) {
        this(port, knowledgeDirectory, modelApiStyle, modelBaseUrl,
                modelApiKey, modelName, adminToken, businessApiBaseUrl, businessApiToken,
                "", "", businessApiTimeoutMillis, topK, maxQuestionLength);
    }

    AppConfig(int port, Path knowledgeDirectory,
              String modelApiStyle, String modelBaseUrl,
              String modelApiKey, String modelName, String adminToken,
              String businessApiBaseUrl, String businessApiToken,
              String businessApiUsername, String businessApiPassword, int businessApiTimeoutMillis,
              int topK, int maxQuestionLength) {
        this.port = port;
        this.knowledgeDirectory = knowledgeDirectory;
        this.modelApiStyle = modelApiStyle;
        this.modelBaseUrl = modelBaseUrl;
        this.modelApiKey = modelApiKey;
        this.modelName = modelName;
        this.adminToken = adminToken;
        this.businessApiBaseUrl = stripTrailingSlash(businessApiBaseUrl);
        this.businessApiToken = businessApiToken;
        this.businessApiUsername = businessApiUsername;
        this.businessApiPassword = businessApiPassword;
        this.businessApiTimeoutMillis = businessApiTimeoutMillis;
        this.topK = topK;
        this.maxQuestionLength = maxQuestionLength;
    }

    static AppConfig fromEnvironment() {
        return new AppConfig(
                integer("AGENT_PORT", 8080),
                Paths.get(value("KNOWLEDGE_DIR", "knowledge")),
                value("MODEL_API_STYLE", "chat_completions").toLowerCase(),
                stripTrailingSlash(value("MODEL_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")),
                value("MODEL_API_KEY", ""),
                value("MODEL_NAME", "qwen-plus"),
                value("ADMIN_TOKEN", ""),
                value("BUSINESS_API_BASE_URL", "http://111.170.148.177:58080"),
                value("BUSINESS_API_TOKEN", ""),
                value("BUSINESS_API_USERNAME", ""),
                value("BUSINESS_API_PASSWORD", ""),
                integer("BUSINESS_API_TIMEOUT_MS", 8000),
                integer("RAG_TOP_K", 4),
                integer("MAX_QUESTION_LENGTH", 2000));
    }

    boolean modelEnabled() {
        return !modelApiKey.trim().isEmpty();
    }

    boolean businessApiEnabled() {
        return !businessApiBaseUrl.trim().isEmpty();
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int integer(String name, int fallback) {
        try {
            return Integer.parseInt(value(name, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
