package com.smart_logistics.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("initial_route_candidate")
public class InitialRouteCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String decisionId;
    private String previewRouteId;
    private String displayName;
    private String provider;
    private String coordinateSystem;
    private Long distanceMeters;
    private Long durationSeconds;
    private String trafficLevel;
    private String trafficSnapshot;
    private String weatherSnapshot;
    private String points;
    @TableField("rank_no")
    private Integer rank;
    private BigDecimal totalScore;
    private String scoreDetails;
    private String reasons;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getPreviewRouteId() { return previewRouteId; }
    public void setPreviewRouteId(String previewRouteId) {
        this.previewRouteId = previewRouteId;
    }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getCoordinateSystem() { return coordinateSystem; }
    public void setCoordinateSystem(String coordinateSystem) {
        this.coordinateSystem = coordinateSystem;
    }
    public Long getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Long distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    public String getTrafficLevel() { return trafficLevel; }
    public void setTrafficLevel(String trafficLevel) { this.trafficLevel = trafficLevel; }
    public String getTrafficSnapshot() { return trafficSnapshot; }
    public void setTrafficSnapshot(String trafficSnapshot) {
        this.trafficSnapshot = trafficSnapshot;
    }
    public String getWeatherSnapshot() { return weatherSnapshot; }
    public void setWeatherSnapshot(String weatherSnapshot) {
        this.weatherSnapshot = weatherSnapshot;
    }
    public String getPoints() { return points; }
    public void setPoints(String points) { this.points = points; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public String getScoreDetails() { return scoreDetails; }
    public void setScoreDetails(String scoreDetails) { this.scoreDetails = scoreDetails; }
    public String getReasons() { return reasons; }
    public void setReasons(String reasons) { this.reasons = reasons; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
