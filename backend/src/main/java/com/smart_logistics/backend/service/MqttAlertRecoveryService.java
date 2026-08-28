package com.smart_logistics.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart_logistics.backend.dto.mqtt.MqttAlertRecoveryPayload;
import com.smart_logistics.backend.entity.Alarm;
import com.smart_logistics.backend.enums.AlarmType;
import com.smart_logistics.backend.mapper.AlarmMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

@Service
public class MqttAlertRecoveryService {

    public enum RecoveryResult {
        RECOVERED,
        ALARM_NOT_FOUND
    }

    private static final ZoneId DATABASE_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern DEVICE_CODE_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final AlarmMapper alarmMapper;
    private final AlarmResolutionService alarmResolutionService;

    public MqttAlertRecoveryService(AlarmMapper alarmMapper,
                                    AlarmResolutionService alarmResolutionService) {
        this.alarmMapper = alarmMapper;
        this.alarmResolutionService = alarmResolutionService;
    }

    @Transactional
    public RecoveryResult recover(MqttAlertRecoveryPayload payload) {
        ValidatedRecovery recovery = validate(payload);
        Alarm alarm = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getDeviceCode, payload.vehicleId())
                .eq(Alarm::getAlarmType, recovery.alarmType().name())
                .eq(Alarm::getOccurredAt, LocalDateTime.ofInstant(
                        recovery.triggeredAt(), DATABASE_TIME_ZONE))
                .orderByDesc(Alarm::getId)
                .last("LIMIT 1"));
        if (alarm == null || alarm.getId() == null) {
            return RecoveryResult.ALARM_NOT_FOUND;
        }
        alarmResolutionService.markConditionRecovered(alarm.getId(), recovery.recoveredAt());
        return RecoveryResult.RECOVERED;
    }

    private ValidatedRecovery validate(MqttAlertRecoveryPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("alert recovery payload is required");
        }
        if (!"1.0".equals(payload.schemaVersion())) {
            throw new IllegalArgumentException("unsupported alert recovery schema_version");
        }
        if (payload.vehicleId() == null
                || !DEVICE_CODE_PATTERN.matcher(payload.vehicleId()).matches()) {
            throw new IllegalArgumentException("invalid alert recovery vehicle_id");
        }
        AlarmType alarmType = MqttAlertIngestionService.parseAlarmType(payload.alertType());
        if (!"RECOVERED".equals(payload.conditionStatus())) {
            throw new IllegalArgumentException("invalid alert recovery condition_status");
        }
        if (!"device".equals(payload.source())) {
            throw new IllegalArgumentException("invalid alert recovery source");
        }
        try {
            Instant triggeredAt = OffsetDateTime.parse(payload.triggeredAt()).toInstant();
            Instant recoveredAt = OffsetDateTime.parse(payload.recoveredAt()).toInstant();
            if (recoveredAt.isBefore(triggeredAt)) {
                throw new IllegalArgumentException(
                        "alert recovery time precedes trigger time");
            }
            return new ValidatedRecovery(alarmType, triggeredAt, recoveredAt);
        } catch (DateTimeException | NullPointerException exception) {
            throw new IllegalArgumentException("invalid alert recovery timestamp", exception);
        }
    }

    private record ValidatedRecovery(AlarmType alarmType, Instant triggeredAt,
                                     Instant recoveredAt) {
    }
}
