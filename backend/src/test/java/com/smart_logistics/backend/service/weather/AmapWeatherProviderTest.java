package com.smart_logistics.backend.service.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmapWeatherProviderTest {

    private final AmapWeatherProvider provider = new AmapWeatherProvider(
            "https://example.invalid/regeo",
            "https://example.invalid/weather",
            "test-key", Duration.ofSeconds(1),
            HttpClient.newHttpClient(), new ObjectMapper());

    @Test
    void buildsCoordinateAndAdcodeRequestsWithWebServiceKey() {
        String regeoUri = provider.buildRegeoUri(106.5516, 29.563).toString();
        String weatherUri = provider.buildWeatherUri("500103").toString();

        assertTrue(regeoUri.contains("location=106.551600,29.563000"));
        assertTrue(regeoUri.contains("extensions=base"));
        assertTrue(weatherUri.contains("city=500103"));
        assertTrue(weatherUri.contains("key=test-key"));
    }

    @Test
    void parsesAdcodeAndSourceAwareLiveWeather() {
        String adcode = provider.parseAdcode("""
                {"status":"1","info":"OK","regeocode":{"addressComponent":{
                  "adcode":"500103"
                }}}
                """);
        WeatherSnapshot weather = provider.parseWeather("""
                {"status":"1","info":"OK","lives":[{
                  "province":"重庆","city":"渝中区","adcode":"500103",
                  "weather":"多云","temperature":"30","winddirection":"西南",
                  "windpower":"≤3","humidity":"59",
                  "reporttime":"2026-08-30 19:02:39"
                }]}
                """, adcode);

        assertEquals("AMAP_WEATHER_V3", weather.source());
        assertEquals("500103", weather.adcode());
        assertEquals("30", weather.temperature().toPlainString());
        assertEquals(59, weather.humidity());
        assertEquals("≤3", weather.windPower());
        assertEquals(OffsetDateTime.parse("2026-08-30T19:02:39+08:00"),
                weather.reportTime());
    }

    @Test
    void rejectsProviderFailureAndMismatchedWeatherRegion() {
        assertThrows(WeatherProviderException.class, () -> provider.parseAdcode("""
                {"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}
                """));
        assertThrows(WeatherProviderException.class, () -> provider.parseWeather("""
                {"status":"1","lives":[{
                  "province":"重庆","city":"江北区","adcode":"500105",
                  "weather":"晴","temperature":"31","winddirection":"南",
                  "windpower":"3","humidity":"50",
                  "reporttime":"2026-08-30 19:00:00"
                }]}
                """, "500103"));
    }
}
