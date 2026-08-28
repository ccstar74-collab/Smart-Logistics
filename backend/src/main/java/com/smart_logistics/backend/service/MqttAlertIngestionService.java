package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.mqtt.MqttAlertPayload;
import com.smart_logistics.backend.dto.realtime.AlarmWsEvent;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.enums.AlarmLevel;
import com.smart_logistics.backend.enums.AlarmConditionStatus;
import com.smart_logistics.backend.enums.AlarmStatus;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.mapper.AlarmMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MqttAlertIngestionService {

    public enum IngestionResult {
        STORED,
        DUPLICATE
    }

    public record AlarmIngestionResult(Long alarmId, boolean created) {
    }

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MqttAlertIngestionService.class);

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
    // 告警位置查找窗口：事件时间前5分钟到后1分钟，取最接近事件时间的GPS点
    private static final Duration LOCATION_LOOKBACK = Duration.ofMinutes(5);
    private static final Duration LOCATION_FORWARD_TOLERANCE = Duration.ofMinutes(1);

    private final AlarmMapper alarmMapper;
    private final AlarmAssociationService associationService;
    private final GpsInfluxService gpsInfluxService;
    private final ApplicationEventPublisher eventPublisher;

    public MqttAlertIngestionService(AlarmMapper alarmMapper,
                                     AlarmAssociationService associationService,
                                     GpsInfluxService gpsInfluxService,
                                     ApplicationEventPublisher eventPublisher) {
        this.alarmMapper = alarmMapper;
        this.associationService = associationService;
        this.gpsInfluxService = gpsInfluxService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public IngestionResult ingest(MqttAlertPayload payload) {
        return toLegacyResult(ingestWithIdentity(payload, null));
    }

    @Transactional
    public IngestionResult ingest(MqttAlertPayload payload, Long taskId) {
        return toLegacyResult(ingestWithIdentity(payload, taskId));
    }

    @Transactional
    public AlarmIngestionResult ingestWithIdentity(MqttAlertPayload payload, Long taskId) {
        ValidatedAlert validated = validate(payload);
        if (taskId != null && taskId <= 0) {
            throw new IllegalArgumentException("invalid alert task_id");
        }

        // 告警归属统一由AlarmAssociationService解析：
        // deviceCode/simCode -> Vehicle -> 当前TRANSPORTING Task，无Task时taskId=null
        AlarmAssociationService.AlarmAssociation association =
                associationService.resolve(payload.vehicleId());
        GpsSample location = locateNear(payload.vehicleId(), validated.occurredAt());

        Alarm alarm = new Alarm();
        alarm.setVehicleId(association.vehicleId());
        // 显式传入的taskId（后端异常检测已确定任务）优先，否则用归属解析结果；
        // 无Task时保留NULL，设备级告警仅调度/管理可见。
        alarm.setTaskId(taskId != null ? taskId : association.taskId());
        alarm.setDeviceCode(payload.vehicleId());
        alarm.setAlarmType(validated.alarmType().name());
        alarm.setLevel(levelFor(validated.alarmType()).name());
        alarm.setMessage(payload.description().trim());
        if (location != null) {
            alarm.setLongitude(BigDecimal.valueOf(location.longitude()));
            alarm.setLatitude(BigDecimal.valueOf(location.latitude()));
        }
        alarm.setStatus(AlarmStatus.UNHANDLED.name());
        alarm.setConditionStatus(AlarmConditionStatus.ACTIVE.name());
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
            // 新告警入库成功，通知/ws/alarms（事务提交后推送）；
            // 幂等去重的重复消息不重复推送
            eventPublisher.publishEvent(AlarmWsEvent.created(alarm.getId()));
            return new AlarmIngestionResult(alarm.getId(), true);
        } catch (DuplicateKeyException exception) {
            Alarm existing = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                    .eq(Alarm::getEventKey, alarm.getEventKey())
                    .last("LIMIT 1"));
            if (existing == null || existing.getId() == null) {
                throw new DataAccessResourceFailureException(
                        "duplicate alert exists but its identity is unavailable", exception);
            }
            return new AlarmIngestionResult(existing.getId(), false);
        }
    }

    /**
     * 尽力捕获告警发生时的车辆位置。位置缺失或时序数据源不可用时返回NULL，
     * 不能因此阻塞告警入库。
     */
    private GpsSample locateNear(String deviceCode, Instant occurredAt) {
        try {
            return gpsInfluxService.querySamples(
                            List.of(deviceCode),
                            occurredAt.minus(LOCATION_LOOKBACK),
                            occurredAt.plus(LOCATION_FORWARD_TOLERANCE))
                    .stream()
                    .min(Comparator.comparing(sample -> Duration.between(
                            sample.collectedAt(), occurredAt).abs()))
                    .orElse(null);
        } catch (RuntimeException exception) {
            LOGGER.warn("告警位置查找失败，跳过位置记录 deviceCode={}, reason={}",
                    deviceCode, exception.getMessage());
            return null;
        }
    }

    private IngestionResult toLegacyResult(AlarmIngestionResult result) {
        return result.created() ? IngestionResult.STORED : IngestionResult.DUPLICATE;
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
