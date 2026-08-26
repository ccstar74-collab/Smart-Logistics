package com.smart_logistics.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
@TableName("vehicle")
public class Vehicle {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String plateNumber;
    private String type;
    private BigDecimal capacity;
    private String status;
    private Long driverId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal lastLongitude;
    private BigDecimal lastLatitude;
    private LocalDateTime lastUpdatedAt;
    // =====新增simCode字段=====
    // 显式绑定数据库 sim_code 字段，Java属性驼峰 simCode
    @TableField("sim_code")
    private String simCode;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BigDecimal getLastLongitude() {
        return lastLongitude;
    }

    public void setLastLongitude(BigDecimal lastLongitude) {
        this.lastLongitude = lastLongitude;
    }

    public BigDecimal getLastLatitude() {
        return lastLatitude;
    }

    public void setLastLatitude(BigDecimal lastLatitude) {
        this.lastLatitude = lastLatitude;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public String getSimCode() {
        return simCode;
    }

    public void setSimCode(String simCode) {
        this.simCode = simCode;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }
}
