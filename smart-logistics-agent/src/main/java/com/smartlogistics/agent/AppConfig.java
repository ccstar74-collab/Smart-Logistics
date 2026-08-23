package com.smartlogistics.agent;

import java.nio.file.Path;
import java.nio.file.Paths;

final class AppConfig {
    final int port;
    final Path knowledgeDirectory;
    final Path realtimeDataDirectory;
    final String modelApiStyle;
    final String modelBaseUrl;
    final String modelApiKey;
    final String modelName;
    final String adminToken;
    final int topK;
    final int maxQuestionLength;

    AppConfig(int port, Path knowledgeDirectory, Path realtimeDataDirectory,
              String modelApiStyle, String modelBaseUrl,
              String modelApiKey, String modelName, String adminToken,
              int topK, int maxQuestionLength) {
        this.port = port;
        this.knowledgeDirectory = knowledgeDirectory;
        this.realtimeDataDirectory = realtimeDataDirectory;
        this.modelApiStyle = modelApiStyle;
        this.modelBaseUrl = modelBaseUrl;
        this.modelApiKey = modelApiKey;
        this.modelName = modelName;
        this.adminToken = adminToken;
        this.topK = topK;
        this.maxQuestionLength = maxQuestionLength;
    }

    static AppConfig fromEnvironment() {
        return new AppConfig(
                integer("AGENT_PORT", 8080),
                Paths.get(value("KNOWLEDGE_DIR", "knowledge")),
                Paths.get(value("CARLA_DATA_DIR", "planning_guoyuan_20v_30tasks")),
                value("MODEL_API_STYLE", "chat_completions").toLowerCase(),
                stripTrailingSlash(value("MODEL_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")),
                value("MODEL_API_KEY", ""),
                value("MODEL_NAME", "qwen-plus"),
                value("ADMIN_TOKEN", ""),
                integer("RAG_TOP_K", 4),
                integer("MAX_QUESTION_LENGTH", 2000));
    }

    boolean modelEnabled() {
        return !modelApiKey.trim().isEmpty();
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
