package com.smart_logistics.backend.service.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class AmapWeatherProvider implements WeatherProvider {

    private static final String SOURCE = "AMAP_WEATHER_V3";
    private static final ZoneId API_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter REPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String regeoEndpoint;
    private final String weatherEndpoint;
    private final String apiKey;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AmapWeatherProvider(
            @Value("${app.weather.amap.regeo-endpoint:"
                    + "https://restapi.amap.com/v3/geocode/regeo}") String regeoEndpoint,
            @Value("${app.weather.amap.weather-endpoint:"
                    + "https://restapi.amap.com/v3/weather/weatherInfo}") String weatherEndpoint,
            @Value("${app.weather.amap.key:}") String apiKey,
            @Value("${app.weather.amap.request-timeout:PT5S}") Duration requestTimeout) {
        this(regeoEndpoint, weatherEndpoint, apiKey, requestTimeout,
                HttpClient.newBuilder().connectTimeout(requestTimeout).build(),
                new ObjectMapper());
    }

    AmapWeatherProvider(String regeoEndpoint, String weatherEndpoint,
                        String apiKey, Duration requestTimeout,
                        HttpClient httpClient, ObjectMapper objectMapper) {
        this.regeoEndpoint = regeoEndpoint;
        this.weatherEndpoint = weatherEndpoint;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeatherSnapshot getCurrentWeather(double longitude, double latitude) {
        requireApiKey();
        String adcode = parseAdcode(request(buildRegeoUri(longitude, latitude),
                "Amap reverse-geocoding"));
        return parseWeather(request(buildWeatherUri(adcode), "Amap weather"), adcode);
    }

    URI buildRegeoUri(double longitude, double latitude) {
        return URI.create(regeoEndpoint + "?location=" + coordinate(longitude, latitude)
                + "&extensions=base&key=" + encode(apiKey));
    }

    URI buildWeatherUri(String adcode) {
        return URI.create(weatherEndpoint + "?city=" + encode(adcode)
                + "&extensions=base&key=" + encode(apiKey));
    }

    String parseAdcode(String body) {
        try {
            JsonNode root = successfulRoot(body, "reverse-geocoding");
            String adcode = root.path("regeocode").path("addressComponent")
                    .path("adcode").asText();
            if (adcode.isBlank()) {
                throw new WeatherProviderException(
                        "Amap reverse-geocoding returned no adcode");
            }
            return adcode;
        } catch (WeatherProviderException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new WeatherProviderException(
                    "Invalid Amap reverse-geocoding response", exception);
        }
    }

    WeatherSnapshot parseWeather(String body, String expectedAdcode) {
        try {
            JsonNode root = successfulRoot(body, "weather");
            JsonNode lives = root.path("lives");
            if (!lives.isArray() || lives.isEmpty()) {
                throw new WeatherProviderException(
                        "Amap weather API returned no live weather");
            }
            JsonNode live = lives.get(0);
            String adcode = requiredText(live, "adcode");
            if (!expectedAdcode.equals(adcode)) {
                throw new WeatherProviderException(
                        "Amap weather adcode does not match requested region");
            }
            return new WeatherSnapshot(
                    SOURCE,
                    adcode,
                    live.path("province").asText(),
                    live.path("city").asText(),
                    requiredText(live, "weather"),
                    new BigDecimal(requiredText(live, "temperature")),
                    parseHumidity(requiredText(live, "humidity")),
                    live.path("winddirection").asText(),
                    requiredText(live, "windpower"),
                    parseReportTime(requiredText(live, "reporttime")));
        } catch (WeatherProviderException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new WeatherProviderException("Invalid Amap weather response", exception);
        }
    }

    private JsonNode successfulRoot(String body, String operation) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            String info = root.path("info").asText("unknown error");
            String infoCode = root.path("infocode").asText("unknown code");
            throw new WeatherProviderException(
                    "Amap " + operation + " API rejected request: "
                            + info + " (" + infoCode + ")");
        }
        return root;
    }

    private String request(URI uri, String operation) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WeatherProviderException(
                        operation + " request failed with HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WeatherProviderException(operation + " request was interrupted", exception);
        } catch (IOException exception) {
            throw new WeatherProviderException(operation + " request failed", exception);
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new WeatherProviderException(
                    "AMAP_WEB_SERVICE_KEY is not configured");
        }
    }

    private String coordinate(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new WeatherProviderException("weather coordinate is outside valid range");
        }
        return String.format(Locale.ROOT, "%.6f,%.6f", longitude, latitude);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requiredText(JsonNode parent, String name) {
        String value = parent.path(name).asText();
        if (value.isBlank()) {
            throw new WeatherProviderException(
                    "Amap weather response is missing " + name);
        }
        return value;
    }

    private int parseHumidity(String value) {
        int humidity = Integer.parseInt(value);
        if (humidity < 0 || humidity > 100) {
            throw new WeatherProviderException(
                    "Amap weather response has invalid humidity");
        }
        return humidity;
    }

    private OffsetDateTime parseReportTime(String value) {
        try {
            return LocalDateTime.parse(value, REPORT_TIME_FORMAT)
                    .atZone(API_TIME_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            throw new WeatherProviderException(
                    "Amap weather response has invalid reporttime", exception);
        }
    }
}
