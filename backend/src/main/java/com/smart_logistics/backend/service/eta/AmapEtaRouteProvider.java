package com.smart_logistics.backend.service.eta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AmapEtaRouteProvider implements EtaRouteProvider {

    private final String endpoint;
    private final String apiKey;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AmapEtaRouteProvider(
            @Value("${app.eta.amap.endpoint:https://restapi.amap.com/v3/direction/driving}")
            String endpoint,
            @Value("${app.eta.amap.key:}") String apiKey,
            @Value("${app.eta.amap.request-timeout:PT5S}") Duration requestTimeout) {
        this(endpoint, apiKey, requestTimeout,
                HttpClient.newBuilder().connectTimeout(requestTimeout).build(),
                new ObjectMapper());
    }

    AmapEtaRouteProvider(String endpoint, String apiKey, Duration requestTimeout,
                         HttpClient httpClient, ObjectMapper objectMapper) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public EtaPlannedRoute plan(double startLongitude, double startLatitude,
                                double endLongitude, double endLatitude) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EtaProviderException("AMAP_WEB_SERVICE_KEY is not configured");
        }
        HttpRequest request = HttpRequest.newBuilder(buildUri(
                        startLongitude, startLatitude, endLongitude, endLatitude))
                .timeout(requestTimeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EtaProviderException(
                        "Amap route request failed with HTTP " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EtaProviderException("Amap route request was interrupted", exception);
        } catch (IOException exception) {
            throw new EtaProviderException("Amap route request failed", exception);
        }
    }

    URI buildUri(double startLongitude, double startLatitude,
                 double endLongitude, double endLatitude) {
        String query = "origin=" + coordinate(startLongitude, startLatitude)
                + "&destination=" + coordinate(endLongitude, endLatitude)
                + "&strategy=0&extensions=base&key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(endpoint + "?" + query);
    }

    private String coordinate(double longitude, double latitude) {
        return String.format(Locale.ROOT, "%.6f,%.6f",
                longitude, latitude);
    }

    EtaPlannedRoute parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                String info = root.path("info").asText("unknown error");
                String infoCode = root.path("infocode").asText("unknown code");
                throw new EtaProviderException(
                        "Amap route API rejected request: " + info + " (" + infoCode + ")");
            }
            JsonNode path = root.path("route").path("paths").path(0);
            if (path.isMissingNode()) {
                throw new EtaProviderException("Amap route API returned no driving path");
            }
            long distanceMeters = parsePositiveLong(path.path("distance"), "distance");
            long durationSeconds = parsePositiveLong(path.path("duration"), "duration");
            List<EtaCoordinate> polyline = parsePolyline(path.path("steps"));
            return new EtaPlannedRoute(
                    polyline, distanceMeters, Duration.ofSeconds(durationSeconds));
        } catch (EtaProviderException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new EtaProviderException("Invalid Amap route response", exception);
        }
    }

    private List<EtaCoordinate> parsePolyline(JsonNode steps) {
        List<EtaCoordinate> points = new ArrayList<>();
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                String encoded = step.path("polyline").asText();
                if (encoded.isBlank()) continue;
                for (String pair : encoded.split(";")) {
                    String[] values = pair.split(",");
                    if (values.length != 2) {
                        throw new EtaProviderException("Amap route polyline is invalid");
                    }
                    EtaCoordinate coordinate = new EtaCoordinate(
                            Double.parseDouble(values[0]), Double.parseDouble(values[1]));
                    if (points.isEmpty() || !points.getLast().equals(coordinate)) {
                        points.add(coordinate);
                    }
                }
            }
        }
        if (points.size() < 2) {
            throw new EtaProviderException("Amap route API returned no usable polyline");
        }
        return points;
    }

    private long parsePositiveLong(JsonNode node, String name) {
        String value = node.asText();
        if (value == null || value.isBlank()) {
            throw new EtaProviderException("Amap route response is missing " + name);
        }
        long parsed = Math.round(Double.parseDouble(value));
        if (parsed <= 0) {
            throw new EtaProviderException("Amap route response has invalid " + name);
        }
        return parsed;
    }
}
