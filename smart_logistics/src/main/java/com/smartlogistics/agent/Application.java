package com.smartlogistics.agent;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class Application {
    private Application() {}

    public static void main(String[] args) throws Exception {
        final AppConfig config = AppConfig.fromEnvironment();
        final KnowledgeBase knowledgeBase = new KnowledgeBase(config.knowledgeDirectory.toAbsolutePath().normalize());
        int chunkCount = knowledgeBase.reload();
        final BusinessDataService businessDataService = new BusinessDataService(
                config.businessApiBaseUrl,
                config.businessApiToken,
                config.businessApiUsername,
                config.businessApiPassword,
                config.businessApiTimeoutMillis);
        businessDataService.startRealtime(System.getenv().getOrDefault("REALTIME_WS_URL", "ws://111.170.148.177:58080/ws/vehicle-locations"));
        businessDataService.configureAmap(
                System.getenv().getOrDefault("AMAP_WEB_SERVICE_KEY", ""),
                System.getenv().getOrDefault("AMAP_REVERSE_GEOCODE_URL", ""),
                System.getenv().getOrDefault("AMAP_GEOCODE_URL", ""));
        final ModelClient modelClient = ModelClients.create(config);
        final LogisticsAgent agent = new LogisticsAgent(
                knowledgeBase, modelClient, businessDataService, config);
        final RouteRecommendationService routeRecommendationService = new RouteRecommendationService(modelClient);
        final InitialRouteRecommendationService initialRouteRecommendationService = new InitialRouteRecommendationService();

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.createContext("/health", exchange -> {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("status", "UP");
            data.put("knowledgeChunks", knowledgeBase.size());
            data.put("modelEnabled", config.modelEnabled());
            data.put("modelApiStyle", config.modelApiStyle);
            data.put("modelName", config.modelName);
            data.put("businessDataEnabled", businessDataService.enabled());
            data.put("businessApiBaseUrl", config.businessApiBaseUrl);
            data.put("routeScoringEnabled", true);
            data.put("routeScoringAlgorithmVersion", "route-score-v1");
            data.put("initialRouteScoringEnabled", true);
            data.put("initialRouteScoringRuleVersion", InitialRouteRecommendationService.VERSION);
            sendJson(exchange, 200, data);
        });
        server.createContext("/api/chat", exchange -> handle(exchange, () -> {
            requireMethod(exchange, "POST");
            Map<String, Object> body = Json.object(readBody(exchange, 64 * 1024));
            String sessionId = string(body.get("sessionId"));
            String question = string(body.get("question"));
            String requestToken = bearerToken(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, agent.chat(sessionId, question, requestToken).toMap());
        }));
        server.createContext("/api/route-recommendations/score", exchange -> handle(exchange, () -> {
            requireMethod(exchange, "POST");
            String requestToken = bearerToken(exchange.getRequestHeaders().getFirst("Authorization"));
            if (requestToken.isEmpty()) throw new SecurityException("路线评分必须提供调用方 Bearer Token");
            Map<String,Object> body = Json.object(readBody(exchange, 512 * 1024));
            sendJson(exchange, 200, routeRecommendationService.score(body));
        }));
        server.createContext("/api/route-recommendations/initial", exchange -> handle(exchange, () -> {
            requireMethod(exchange, "POST");
            String requestToken = bearerToken(exchange.getRequestHeaders().getFirst("Authorization"));
            if (requestToken.isEmpty()) throw new SecurityException("初始路线评分必须提供调用方 Bearer Token");
            Map<String,Object> body = Json.object(readBody(exchange, 512 * 1024));
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", initialRouteRecommendationService.score(body));
            sendJson(exchange, 200, response);
        }));
        server.createContext("/api/knowledge", exchange -> handle(exchange, () -> {
            requireMethod(exchange, "POST");
            requireAdmin(exchange, config);
            Map<String, Object> body = Json.object(readBody(exchange, 1024 * 1024));
            knowledgeBase.addDocument(string(body.get("title")), string(body.get("content")));
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("status", "indexed");
            response.put("knowledgeChunks", knowledgeBase.size());
            sendJson(exchange, 201, response);
        }));
        server.createContext("/", exchange -> {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendEmpty(exchange, 204);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/".equals(path)) {
                sendWebPage(exchange, Paths.get("web", "index.html"));
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/favicon.ico".equals(path)) {
                sendEmpty(exchange, 204);
            } else {
                sendError(exchange, 404, "NOT_FOUND", "接口不存在");
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start();
        System.out.println("智慧物流智能体已启动：http://localhost:" + config.port);
        System.out.println("知识片段：" + chunkCount + "，模型：" + (config.modelEnabled() ? config.modelName : "未配置（检索模式）"));
        System.out.println("云端业务数据：" + (businessDataService.enabled() ? config.businessApiBaseUrl : "未配置"));
    }

    private static void handle(HttpExchange exchange, CheckedAction action) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 204);
            return;
        }
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "BAD_REQUEST", e.getMessage());
        } catch (SecurityException e) {
            sendError(exchange, 401, "UNAUTHORIZED", e.getMessage());
        } catch (MethodNotAllowedException e) {
            exchange.getResponseHeaders().set("Allow", e.method);
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "娴犲懏鏁幐?" + e.method);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            sendError(exchange, 502, "AGENT_ERROR", "閺呴缚鍏樻担鎾存畯閺冭埖妫ゅ▔鏇炵暚閹存劘顕Ч鍌︾窗" + e.getMessage());
        }
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) throw new MethodNotAllowedException(method);
    }

    private static void requireAdmin(HttpExchange exchange, AppConfig config) {
        if (config.adminToken.isEmpty()) throw new SecurityException("閻儴鐦戞惔鎾冲晸閸忋儲甯撮崣锝嗘弓閸氼垳鏁ら敍宀冾嚞闁板秶鐤?ADMIN_TOKEN");
        String supplied = exchange.getRequestHeaders().getFirst("X-Admin-Token");
        if (!constantTimeEquals(config.adminToken, supplied)) throw new SecurityException("管理员凭证无效");
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) return false;
        int different = expected.length() ^ supplied.length();
        int length = Math.max(expected.length(), supplied.length());
        for (int i = 0; i < length; i++) {
            char a = i < expected.length() ? expected.charAt(i) : 0;
            char b = i < supplied.length() ? supplied.charAt(i) : 0;
            different |= a ^ b;
        }
        return different == 0;
    }

    private static String readBody(HttpExchange exchange, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        try (InputStream input = exchange.getRequestBody()) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > limit) throw new IllegalArgumentException("请求体过大");
                output.write(buffer, 0, count);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }

    private static String bearerToken(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) return "";
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new SecurityException("Authorization 必须使用 Bearer Token");
        }
        String token = value.substring(7).trim();
        if (token.isEmpty() || token.length() > 4096) throw new SecurityException("Authorization Token 无效");
        return token;
    }

    private static void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", message == null ? "閺堫亞鐓￠柨娆掝嚖" : message);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", error);
        sendJson(exchange, status, body);
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        addCommonHeaders(exchange.getResponseHeaders());
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        addCommonHeaders(exchange.getResponseHeaders());
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void sendWebPage(HttpExchange exchange, Path page) throws IOException {
        Path target = page.toAbsolutePath().normalize();
        if (!Files.isRegularFile(target)) {
            sendError(exchange, 503, "WEB_PAGE_MISSING", "网页文件不存在");
            return;
        }
        byte[] bytes = Files.readAllBytes(target);
        addCommonHeaders(exchange.getResponseHeaders());
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static void addCommonHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Admin-Token");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("X-Content-Type-Options", "nosniff");
    }

    private interface CheckedAction { void run() throws Exception; }
    private static final class MethodNotAllowedException extends RuntimeException {
        final String method;
        MethodNotAllowedException(String method) { this.method = method; }
    }
}

