package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.enums.TransportTaskRouteStatus;

import java.time.OffsetDateTime;

public class TransportTaskResponse {

    private final Long id;
    private final String taskNo;
    private final Long cargoId;
    private final Long vehicleId;
    private final Long originWarehouseId;
    private final Long driverId;
    private final String driverName;
    private final String plateNumber;
    private final String routeId;
    private final Integer routeVersion;
    private final TransportTaskRouteStatus routeStatus;
    private final String startLocation;
    private final Double startLongitude;
    private final Double startLatitude;
    private final String endLocation;
    private final Double endLongitude;
    private final Double endLatitude;
    private final OffsetDateTime planStartTime;
    private final OffsetDateTime planEndTime;
    private final OffsetDateTime actualStartTime;
    private final OffsetDateTime actualEndTime;
    private final TransportTaskStatus status;
    private final OffsetDateTime estimatedArrivalTime;
    private final OffsetDateTime etaCalculatedAt;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public TransportTaskResponse(Long id, String taskNo, Long cargoId, Long vehicleId,
                                 String startLocation, String endLocation,
                                 OffsetDateTime planStartTime, OffsetDateTime planEndTime,
                                 OffsetDateTime actualStartTime, OffsetDateTime actualEndTime,
                                 TransportTaskStatus status, OffsetDateTime estimatedArrivalTime,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, taskNo, cargoId, vehicleId, startLocation, null, null,
                endLocation, null, null, planStartTime, planEndTime,
                actualStartTime, actualEndTime, status, estimatedArrivalTime,
                null, createdAt, updatedAt);
    }

    public TransportTaskResponse(Long id, String taskNo, Long cargoId, Long vehicleId,
                                 String startLocation, Double startLongitude,
                                 Double startLatitude, String endLocation,
                                 Double endLongitude, Double endLatitude,
                                 OffsetDateTime planStartTime, OffsetDateTime planEndTime,
                                 OffsetDateTime actualStartTime, OffsetDateTime actualEndTime,
                                 TransportTaskStatus status, OffsetDateTime estimatedArrivalTime,
                                 OffsetDateTime etaCalculatedAt,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, taskNo, cargoId, vehicleId, startLocation, startLongitude,
                startLatitude, endLocation, endLongitude, endLatitude,
                planStartTime, planEndTime, actualStartTime, actualEndTime,
                status, estimatedArrivalTime, etaCalculatedAt, createdAt, updatedAt,
                null, null, null, null, null, null);
    }

    public TransportTaskResponse(Long id, String taskNo, Long cargoId, Long vehicleId,
                                 String startLocation, Double startLongitude,
                                 Double startLatitude, String endLocation,
                                 Double endLongitude, Double endLatitude,
                                 OffsetDateTime planStartTime, OffsetDateTime planEndTime,
                                 OffsetDateTime actualStartTime, OffsetDateTime actualEndTime,
                                 TransportTaskStatus status, OffsetDateTime estimatedArrivalTime,
                                 OffsetDateTime etaCalculatedAt,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                 Long driverId, String driverName, String plateNumber,
                                 String routeId, Integer routeVersion,
                                 TransportTaskRouteStatus routeStatus) {
        this(id, taskNo, cargoId, vehicleId, startLocation, startLongitude,
                startLatitude, endLocation, endLongitude, endLatitude,
                planStartTime, planEndTime, actualStartTime, actualEndTime,
                status, estimatedArrivalTime, etaCalculatedAt, createdAt, updatedAt,
                driverId, driverName, plateNumber, routeId, routeVersion, routeStatus, null);
    }

    public TransportTaskResponse(Long id, String taskNo, Long cargoId, Long vehicleId,
                                 String startLocation, Double startLongitude,
                                 Double startLatitude, String endLocation,
                                 Double endLongitude, Double endLatitude,
                                 OffsetDateTime planStartTime, OffsetDateTime planEndTime,
                                 OffsetDateTime actualStartTime, OffsetDateTime actualEndTime,
                                 TransportTaskStatus status, OffsetDateTime estimatedArrivalTime,
                                 OffsetDateTime etaCalculatedAt,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                 Long driverId, String driverName, String plateNumber,
                                 String routeId, Integer routeVersion,
                                 TransportTaskRouteStatus routeStatus,
                                 Long originWarehouseId) {
        this.id = id;
        this.taskNo = taskNo;
        this.cargoId = cargoId;
        this.vehicleId = vehicleId;
        this.originWarehouseId = originWarehouseId;
        this.driverId = driverId;
        this.driverName = driverName;
        this.plateNumber = plateNumber;
        this.routeId = routeId;
        this.routeVersion = routeVersion;
        this.routeStatus = routeStatus;
        this.startLocation = startLocation;
        this.startLongitude = startLongitude;
        this.startLatitude = startLatitude;
        this.endLocation = endLocation;
        this.endLongitude = endLongitude;
        this.endLatitude = endLatitude;
        this.planStartTime = planStartTime;
        this.planEndTime = planEndTime;
        this.actualStartTime = actualStartTime;
        this.actualEndTime = actualEndTime;
        this.status = status;
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.etaCalculatedAt = etaCalculatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getTaskNo() { return taskNo; }
    public Long getCargoId() { return cargoId; }
    public Long getVehicleId() { return vehicleId; }
    public Long getOriginWarehouseId() { return originWarehouseId; }
    public Long getDriverId() { return driverId; }
    public String getDriverName() { return driverName; }
    public String getPlateNumber() { return plateNumber; }
    public String getRouteId() { return routeId; }
    public Integer getRouteVersion() { return routeVersion; }
    public TransportTaskRouteStatus getRouteStatus() { return routeStatus; }
    public String getStartLocation() { return startLocation; }
    public Double getStartLongitude() { return startLongitude; }
    public Double getStartLatitude() { return startLatitude; }
    public String getEndLocation() { return endLocation; }
    public Double getEndLongitude() { return endLongitude; }
    public Double getEndLatitude() { return endLatitude; }
    public OffsetDateTime getPlanStartTime() { return planStartTime; }
    public OffsetDateTime getPlanEndTime() { return planEndTime; }
    public OffsetDateTime getActualStartTime() { return actualStartTime; }
    public OffsetDateTime getActualEndTime() { return actualEndTime; }
    public TransportTaskStatus getStatus() { return status; }
    public OffsetDateTime getEstimatedArrivalTime() { return estimatedArrivalTime; }
    public OffsetDateTime getEtaCalculatedAt() { return etaCalculatedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
