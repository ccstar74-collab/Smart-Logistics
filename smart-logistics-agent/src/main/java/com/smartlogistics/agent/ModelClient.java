package com.smartlogistics.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

interface ModelClient {
    String answer(String instructions, String input) throws IOException;
    boolean enabled();
}

final class ModelClients {
    private ModelClients() {}

    static ModelClient create(AppConfig config) {
        if ("chat_completions".equals(config.modelApiStyle)) {
            return new ChatCompletionsApiModelClient(config);
        }
        if ("responses".equals(config.modelApiStyle)) {
            return new ResponsesApiModelClient(config);
        }
        throw new IllegalArgumentException(
                "不支持的 MODEL_API_STYLE：" + config.modelApiStyle
                        + "；可选值为 chat_completions 或 responses");
    }
}

final class ChatCompletionsApiModelClient implements ModelClient {
    private final AppConfig config;
    ChatCompletionsApiModelClient(AppConfig config) { this.config = config; }

    public boolean enabled() { return config.modelEnabled(); }

    public String answer(String instructions, String input) throws IOException {
        if (!enabled()) throw new IllegalStateException("模型未配置");

        Map<String, Object> systemMessage = new LinkedHashMap<String, Object>();
        systemMessage.put("role", "system");
        systemMessage.put("content", instructions);
        Map<String, Object> userMessage = new LinkedHashMap<String, Object>();
        userMessage.put("role", "user");
        userMessage.put("content", input);

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", config.modelName);
        body.put("messages", Arrays.<Object>asList(systemMessage, userMessage));
        body.put("stream", Boolean.FALSE);

        Map<String, Object> response = ModelHttp.post(
                config.modelBaseUrl + "/chat/completions", config.modelApiKey, body);
        return extractText(response);
    }

    @SuppressWarnings("unchecked")
    static String extractText(Map<String, Object> response) throws IOException {
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List) || ((List<?>) choicesValue).isEmpty()) {
            throw new IOException("模型响应中没有 choices");
        }
        Object firstChoice = ((List<?>) choicesValue).get(0);
        if (!(firstChoice instanceof Map)) throw new IOException("模型响应 choices 格式非法");
        Object messageValue = ((Map<String, Object>) firstChoice).get("message");
        if (!(messageValue instanceof Map)) throw new IOException("模型响应中没有 message");
        Object content = ((Map<String, Object>) messageValue).get("content");
        if (content instanceof String && !((String) content).trim().isEmpty()) {
            return (String) content;
        }
        throw new IOException("模型响应中没有可用文本");
    }
}

final class ResponsesApiModelClient implements ModelClient {
    private final AppConfig config;
    ResponsesApiModelClient(AppConfig config) { this.config = config; }

    public boolean enabled() { return config.modelEnabled(); }

    public String answer(String instructions, String input) throws IOException {
        if (!enabled()) throw new IllegalStateException("模型未配置");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", config.modelName);
        body.put("instructions", instructions);
        body.put("input", input);
        return extractText(ModelHttp.post(config.modelBaseUrl + "/responses", config.modelApiKey, body));
    }

    @SuppressWarnings("unchecked")
    private static String extractText(Map<String, Object> response) throws IOException {
        Object direct = response.get("output_text");
        if (direct instanceof String && !((String) direct).trim().isEmpty()) return (String) direct;
        Object output = response.get("output");
        if (output instanceof List) {
            StringBuilder text = new StringBuilder();
            for (Object item : (List<?>) output) {
                if (!(item instanceof Map)) continue;
                Object content = ((Map<String, Object>) item).get("content");
                if (!(content instanceof List)) continue;
                for (Object part : (List<?>) content) {
                    if (!(part instanceof Map)) continue;
                    Object value = ((Map<String, Object>) part).get("text");
                    if (value instanceof String) {
                        if (text.length() > 0) text.append('\n');
                        text.append(value);
                    }
                }
            }
            if (text.length() > 0) return text.toString();
        }
        throw new IOException("模型响应中没有可用文本");
    }

}

final class ModelHttp {
    private ModelHttp() {}

    static Map<String, Object> post(String endpoint, String apiKey, Map<String, Object> body) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }

        int status = connection.getResponseCode();
        String response = read(status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream());
        if (status < 200 || status >= 300) {
            throw new IOException("模型服务返回 HTTP " + status + ": " + abbreviate(response, 1000));
        }
        try {
            return Json.object(response);
        } catch (IllegalArgumentException e) {
            throw new IOException("模型服务返回了非法 JSON：" + abbreviate(response, 500), e);
        }
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static String abbreviate(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length) + "...";
    }
}
