package com.smart_logistics.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("initial_route_decision")
public class InitialRouteDecision {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String decisionId;
    private Long createdBy;
    private Long originWarehouseId;
    private String status;
    private String planningMode;
    private String planningResult;
    private String startSnapshot;
    private String destinationSnapshot;
    private String recommendedRouteId;
    private String selectedRouteId;
    private String routeSelectionRemark;
    private String scoringRuleVersion;
    private String recommendationSource;
    private String inputSnapshot;
    private String weatherSnapshot;
    private String explanation;
    private String idempotencyKey;
    private String confirmationIdempotencyKey;
    private LocalDateTime calculatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private Long taskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getOriginWarehouseId() { return originWarehouseId; }
    public void setOriginWarehouseId(Long originWarehouseId) {
        this.originWarehouseId = originWarehouseId;
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlanningMode() { return planningMode; }
    public void setPlanningMode(String planningMode) { this.planningMode = planningMode; }
    public String getPlanningResult() { return planningResult; }
    public void setPlanningResult(String planningResult) {
        this.planningResult = planningResult;
    }
    public String getStartSnapshot() { return startSnapshot; }
    public void setStartSnapshot(String startSnapshot) { this.startSnapshot = startSnapshot; }
    public String getDestinationSnapshot() { return destinationSnapshot; }
    public void setDestinationSnapshot(String destinationSnapshot) {
        this.destinationSnapshot = destinationSnapshot;
    }
    public String getRecommendedRouteId() { return recommendedRouteId; }
    public void setRecommendedRouteId(String recommendedRouteId) {
        this.recommendedRouteId = recommendedRouteId;
    }
    public String getSelectedRouteId() { return selectedRouteId; }
    public void setSelectedRouteId(String selectedRouteId) {
        this.selectedRouteId = selectedRouteId;
    }
    public String getRouteSelectionRemark() { return routeSelectionRemark; }
    public void setRouteSelectionRemark(String routeSelectionRemark) {
        this.routeSelectionRemark = routeSelectionRemark;
    }
    public String getScoringRuleVersion() { return scoringRuleVersion; }
    public void setScoringRuleVersion(String scoringRuleVersion) {
        this.scoringRuleVersion = scoringRuleVersion;
    }
    public String getRecommendationSource() { return recommendationSource; }
    public void setRecommendationSource(String recommendationSource) {
        this.recommendationSource = recommendationSource;
    }
    public String getInputSnapshot() { return inputSnapshot; }
    public void setInputSnapshot(String inputSnapshot) { this.inputSnapshot = inputSnapshot; }
    public String getWeatherSnapshot() { return weatherSnapshot; }
    public void setWeatherSnapshot(String weatherSnapshot) {
        this.weatherSnapshot = weatherSnapshot;
    }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    public String getConfirmationIdempotencyKey() {
        return confirmationIdempotencyKey;
    }
    public void setConfirmationIdempotencyKey(String confirmationIdempotencyKey) {
        this.confirmationIdempotencyKey = confirmationIdempotencyKey;
    }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
