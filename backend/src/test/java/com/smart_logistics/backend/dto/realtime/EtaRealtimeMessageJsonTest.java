package com.smart_logistics.backend.dto.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用Spring Boot自动配置（@JsonTest切片）的ObjectMapper验证ETA消息序列化
 * 保证GpsWebSocketHandler注入的容器mapper能把OffsetDateTime输出为ISO-8601，
 * 防止有人改回自建Jackson 2 ObjectMapper导致InvalidDefinitionException
 */
@JsonTest
class EtaRealtimeMessageJsonTest {

    @Autowired
    private JacksonTester<EtaRealtimeMessage> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void springManagedMapperWritesEtaFieldsAsIso8601() throws Exception {
        EtaRealtimeMessage message = new EtaRealtimeMessage(
                1L, "real_001",
                OffsetDateTime.parse("2026-08-26T16:30:00+08:00"),
                OffsetDateTime.parse("2026-08-26T16:00:00+08:00"),
                5500, 32.5);

        JsonContent<EtaRealtimeMessage> content = json.write(message);
        JsonNode root = objectMapper.readTree(content.getJson());

        assertTrue(root.get("estimatedArrivalTime").isTextual(),
                "estimatedArrivalTime必须是ISO-8601字符串，不能是时间戳数字");
        assertEquals("2026-08-26T16:30:00+08:00", root.get("estimatedArrivalTime").asText());
        assertTrue(root.get("etaCalculatedAt").isTextual(),
                "etaCalculatedAt必须是ISO-8601字符串，不能是时间戳数字");
        assertEquals("2026-08-26T16:00:00+08:00", root.get("etaCalculatedAt").asText());
        assertEquals("ETA_UPDATED", root.get("type").asText());
        assertEquals(1L, root.get("taskId").asLong());
        assertEquals("real_001", root.get("vehicleId").asText());
    }
}
