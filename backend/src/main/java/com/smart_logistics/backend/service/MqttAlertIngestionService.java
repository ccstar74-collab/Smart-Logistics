package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.mapper.AlarmMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MqttAlertIngestionService {

    public enum IngestionResult {
        STORED,
        DUPLICATE
    }

    private static final ZoneId DATABASE_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern DEVICE_CODE_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Set<String> ALLOWED_SOURCES =
            Set.of("simulator", "backend", "device");
    private static final Map<String, AlarmType> TYPE_MAPPING = Map.of(
            "偏航", AlarmType.ROUTE_DEVIATION,
            "异常停留", AlarmType.ABNORMAL_STOP,
            "异常开箱", AlarmType.ABNORMAL_OPEN
    );

    private final AlarmMapper alarmMapper;

    public MqttAlertIngestionService(AlarmMapper alarmMapper) {
        this.alarmMapper = alarmMapper;
    }

    @Transactional
    public IngestionResult ingest(MqttAlertPayload payload) {
        ValidatedAlert validated = validate(payload);

        Alarm alarm = new Alarm();
        alarm.setTaskId(null);
        alarm.setDeviceCode(payload.vehicleId());
        alarm.setAlarmType(validated.alarmType().name());
        alarm.setLevel(levelFor(validated.alarmType()).name());
        alarm.setMessage(payload.description().trim());
        alarm.setStatus(AlarmStatus.UNHANDLED.name());
        alarm.setSource(payload.source());
        alarm.setSchemaVersion(payload.schemaVersion());
        alarm.setEventKey(createEventKey(payload, validated));
        alarm.setOccurredAt(LocalDateTime.ofInstant(
                validated.occurredAt(), DATABASE_TIME_ZONE));
        alarm.setCreatedAt(LocalDateTime.now(DATABASE_TIME_ZONE));

        try {
            if (alarmMapper.insert(alarm) != 1) {
                throw new DataAccessResourceFailureException(
                        "failed to insert MQTT alert");
            }
            return IngestionResult.STORED;
        } catch (DuplicateKeyException exception) {
            return IngestionResult.DUPLICATE;
        }
    }

    private ValidatedAlert validate(MqttAlertPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("alert payload is required");
        }
        if (!"1.0".equals(payload.schemaVersion())) {
            throw new IllegalArgumentException("unsupported alert schema_version");
        }
        if (payload.vehicleId() == null
                || !DEVICE_CODE_PATTERN.matcher(payload.vehicleId()).matches()) {
            throw new IllegalArgumentException("invalid alert vehicle_id");
        }

        AlarmType alarmType = TYPE_MAPPING.get(payload.alertType());
        if (alarmType == null) {
            throw new IllegalArgumentException("unsupported alert_type");
        }
        if (payload.description() == null
                || payload.description().isBlank()
                || payload.description().length() > 256) {
            throw new IllegalArgumentException("invalid alert description");
        }
        if (!ALLOWED_SOURCES.contains(payload.source())) {
            throw new IllegalArgumentException("invalid alert source");
        }

        try {
            return new ValidatedAlert(
                    alarmType,
                    OffsetDateTime.parse(payload.timestamp()).toInstant()
            );
        } catch (DateTimeException | NullPointerException exception) {
            throw new IllegalArgumentException("invalid alert timestamp", exception);
        }
    }

    private AlarmLevel levelFor(AlarmType alarmType) {
        return switch (alarmType) {
            case ABNORMAL_STOP -> AlarmLevel.MEDIUM;
            case ROUTE_DEVIATION, ABNORMAL_OPEN, OTHER -> AlarmLevel.HIGH;
        };
    }

    private String createEventKey(MqttAlertPayload payload, ValidatedAlert validated) {
        String canonicalValue = String.join("\u0000",
                payload.schemaVersion(),
                payload.vehicleId(),
                validated.alarmType().name(),
                validated.occurredAt().toString()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ValidatedAlert(AlarmType alarmType, Instant occurredAt) {
    }
}
