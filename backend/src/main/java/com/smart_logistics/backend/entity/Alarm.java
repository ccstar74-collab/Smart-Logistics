package com.smart_logistics.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("alarm")
public class Alarm {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long vehicleId;
    private Long taskId;
    private String deviceCode;
    private String alarmType;
    private String level;
    private String message;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordSystem;
    private String status;
    private String conditionStatus;
    private String source;
    private String schemaVersion;
    private String eventKey;
    private LocalDateTime occurredAt;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
    private LocalDateTime createdAt;
    private LocalDateTime recoveredAt;
    private LocalDateTime resolvedAt;
    private String resolutionRemark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public String getCoordSystem() {
        return coordSystem;
    }

    public void setCoordSystem(String coordSystem) {
        this.coordSystem = coordSystem;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(String conditionStatus) {
        this.conditionStatus = conditionStatus;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public String getHandleNote() {
        return handleNote;
    }

    public void setHandleNote(String handleNote) {
        this.handleNote = handleNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getRecoveredAt() {
        return recoveredAt;
    }

    public void setRecoveredAt(LocalDateTime recoveredAt) {
        this.recoveredAt = recoveredAt;
    }

    public String getResolutionRemark() {
        return resolutionRemark;
    }

    public void setResolutionRemark(String resolutionRemark) {
        this.resolutionRemark = resolutionRemark;
    }
}
