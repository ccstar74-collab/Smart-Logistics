package com.smart_logistics.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart_logistics.backend.mapper.TransportTaskRoutePointsTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "transport_task_route", autoResultMap = true)
public class TransportTaskRoute {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeId;
    private Long taskId;
    private String provider;
    private String coordinateSystem;
    @TableField(typeHandler = TransportTaskRoutePointsTypeHandler.class)
    private List<List<Double>> routePoints;
    private Long distanceMeters;
    private Long durationSeconds;
    private Integer routeVersion;
    private String status;
    private LocalDateTime activatedAt;
    private LocalDateTime deactivatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getCoordinateSystem() { return coordinateSystem; }
    public void setCoordinateSystem(String coordinateSystem) {
        this.coordinateSystem = coordinateSystem;
    }
    public List<List<Double>> getRoutePoints() { return routePoints; }
    public void setRoutePoints(List<List<Double>> routePoints) {
        this.routePoints = routePoints;
    }
    public Long getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Long distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    public Integer getRouteVersion() { return routeVersion; }
    public void setRouteVersion(Integer routeVersion) {
        this.routeVersion = routeVersion;
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }
    public LocalDateTime getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(LocalDateTime deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
