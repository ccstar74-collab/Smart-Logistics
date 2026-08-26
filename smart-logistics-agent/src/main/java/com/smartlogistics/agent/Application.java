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
import java.net.URLDecoder;
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
        final VehicleRealtimeService vehicleRealtimeService = new VehicleRealtimeService(config.realtimeDataDirectory);
        final LogisticsAgent agent = new LogisticsAgent(
                knowledgeBase, ModelClients.create(config), vehicleRealtimeService, config);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.createContext("/health", exchange -> {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("status", "UP");
            data.put("knowledgeChunks", knowledgeBase.size());
            data.put("modelEnabled", config.modelEnabled());
            data.put("modelApiStyle", config.modelApiStyle);
            data.put("modelName", config.modelName);
            data.put("realtimeDataEnabled", vehicleRealtimeService.available());
            data.put("realtimeVehicleCount", vehicleRealtimeService.vehicleCount());
            if (vehicleRealtimeService.error() != null) data.put("realtimeDataError", vehicleRealtimeService.error());
            sendJson(exchange, 200, data);
        });
        server.createContext("/api/chat", exchange -> handle(exchange, () -> {
            requireMethod(exchange, "POST");
            Map<String, Object> body = Json.object(readBody(exchange, 64 * 1024));
            String sessionId = string(body.get("sessionId"));
            String question = string(body.get("question"));
            sendJson(exchange, 200, agent.chat(sessionId, question).toMap());
        }));
        server.createContext("/api/v1/vehicles/locations/latest", exchange -> handle(exchange, () -> {
            requireMethod(exchange, "GET");
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", vehicleRealtimeService.latestVehicleMaps());
            response.put("meta", vehicleRealtimeService.metadata());
            sendJson(exchange, 200, response);
        }));
        server.createContext("/api/v1/vehicles/location", exchange -> handle(exchange, () -> {
            requireMethod(exchange, "GET");
            String identifier = queryParameter(exchange, "identifier");
            if (identifier == null || identifier.trim().isEmpty()) {
                throw new IllegalArgumentException("identifier 不能为空");
            }
            VehicleRealtimeService.VehicleSnapshot vehicle = vehicleRealtimeService.find(identifier);
            if (vehicle == null) {
                sendError(exchange, 404, "VEHICLE_NOT_FOUND", "没有找到车辆：" + identifier);
                return;
            }
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", vehicle.toMap());
            response.put("meta", vehicleRealtimeService.metadata());
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
        System.out.println("CARLA 实时车辆：" + vehicleRealtimeService.vehicleCount()
                + "，数据目录：" + vehicleRealtimeService.dataDirectory());
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
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "仅支持 " + e.method);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            sendError(exchange, 502, "AGENT_ERROR", "智能体暂时无法完成请求：" + e.getMessage());
        }
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) throw new MethodNotAllowedException(method);
    }

    private static void requireAdmin(HttpExchange exchange, AppConfig config) {
        if (config.adminToken.isEmpty()) throw new SecurityException("知识库写入接口未启用，请配置 ADMIN_TOKEN");
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

    private static String queryParameter(HttpExchange exchange, String name) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) return null;
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String rawName = separator >= 0 ? part.substring(0, separator) : part;
            if (!name.equals(URLDecoder.decode(rawName, "UTF-8"))) continue;
            String rawValue = separator >= 0 ? part.substring(separator + 1) : "";
            return URLDecoder.decode(rawValue, "UTF-8");
        }
        return null;
    }

    private static void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", message == null ? "未知错误" : message);
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
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("X-Content-Type-Options", "nosniff");
    }

    private interface CheckedAction { void run() throws Exception; }
    private static final class MethodNotAllowedException extends RuntimeException {
        final String method;
        MethodNotAllowedException(String method) { this.method = method; }
    }
}
