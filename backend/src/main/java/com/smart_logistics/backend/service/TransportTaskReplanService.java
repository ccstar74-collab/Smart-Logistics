package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.TransportTaskRouteSnapshot;
import com.smart_logistics.backend.dto.realtime.GpsSample;
import com.smart_logistics.backend.dto.request.TransportTaskReplanRequest;
import com.smart_logistics.backend.dto.response.PlannedRouteResponse;
import com.smart_logistics.backend.dto.response.TransportTaskResponse;
import com.smart_logistics.backend.entity.Vehicle;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaPlannedRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class TransportTaskReplanService {

    private static final String WGS84 = "WGS84";
    private static final int ANCHOR_QUERY_ATTEMPTS = 3;
    private static final Duration ANCHOR_RETRY_INTERVAL = Duration.ofMillis(200);
    private static final double ANCHOR_TOLERANCE_METERS = 5.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final Pattern SIM_CODE_PATTERN = Pattern.compile("^sim_\\d{3}$");

    private final TransportTaskService transportTaskService;
    private final VehicleService vehicleService;
    private final VehicleLocationQueryService locationQueryService;
    private final EtaPlannedRouteService plannedRouteService;
    private final TransportTaskRouteService taskRouteService;
    private final Sleeper sleeper;

    @Autowired
    public TransportTaskReplanService(
            TransportTaskService transportTaskService,
            VehicleService vehicleService,
            VehicleLocationQueryService locationQueryService,
            EtaPlannedRouteService plannedRouteService,
            TransportTaskRouteService taskRouteService) {
        this(transportTaskService, vehicleService, locationQueryService,
                plannedRouteService, taskRouteService,
                duration -> Thread.sleep(duration.toMillis()));
    }

    TransportTaskReplanService(
            TransportTaskService transportTaskService,
            VehicleService vehicleService,
            VehicleLocationQueryService locationQueryService,
            EtaPlannedRouteService plannedRouteService,
            TransportTaskRouteService taskRouteService,
            Sleeper sleeper) {
        this.transportTaskService = transportTaskService;
        this.vehicleService = vehicleService;
        this.locationQueryService = locationQueryService;
        this.plannedRouteService = plannedRouteService;
        this.taskRouteService = taskRouteService;
        this.sleeper = sleeper;
    }

    public PlannedRouteResponse replanFromLatestLocation(
            Long taskId, TransportTaskReplanRequest request) {
        validateAnchorRequest(request);
        TransportTaskResponse task = transportTaskService.getTransportTask(taskId);
        if (task.getStatus() != TransportTaskStatus.TRANSPORTING) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "route can only be replanned for transporting task");
        }
        if (task.getVehicleId() == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task vehicle is missing");
        }
        validateDestination(task.getEndLongitude(), task.getEndLatitude());

        Vehicle vehicle = vehicleService.getVehicleForTransport(task.getVehicleId());
        String simCode = vehicleService.requireTransportSimCode(vehicle);
        if (!Objects.equals(simCode, request.getVehicleDeviceCode())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "vehicleDeviceCode does not match transport task vehicle");
        }
        GpsSample latest = awaitAnchorGps(simCode, request.getPositionAt().toInstant());
        double anchorDistanceMeters = distanceMeters(
                latest.latitude(), latest.longitude(),
                request.getLatitude(), request.getLongitude());
        if (anchorDistanceMeters > ANCHOR_TOLERANCE_METERS) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "latest GPS does not match requested position anchor");
        }
        EtaPlannedRoute plannedRoute = plannedRouteService.planRouteFromWgs84Origin(
                latest.longitude(), latest.latitude(),
                task.getEndLongitude(), task.getEndLatitude());

        TransportTaskRouteSnapshot active = taskRouteService.replaceActiveRouteFromReplan(
                taskId, task.getVehicleId(), plannedRoute);
        return PlannedRouteResponse.from(active, simCode);
    }

    private GpsSample awaitAnchorGps(String simCode, Instant positionAt) {
        for (int attempt = 1; attempt <= ANCHOR_QUERY_ATTEMPTS; attempt++) {
            GpsSample latest = locationQueryService.getLatestOnlineGps(simCode);
            if (!latest.collectedAt().isBefore(positionAt)) {
                return latest;
            }
            if (attempt < ANCHOR_QUERY_ATTEMPTS) {
                sleepBeforeRetry();
            }
        }
        throw new BusinessException(ErrorCode.STATE_CONFLICT,
                "latest GPS does not include requested position anchor");
    }

    private void sleepBeforeRetry() {
        try {
            sleeper.sleep(ANCHOR_RETRY_INTERVAL);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "interrupted while waiting for position anchor");
        }
    }

    private void validateAnchorRequest(TransportTaskReplanRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "replan request must not be null");
        }
        if (!WGS84.equals(request.getCoordinateSystem())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "coordinateSystem must be WGS84");
        }
        if (request.getPositionAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "positionAt must not be null");
        }
        if (request.getVehicleDeviceCode() == null
                || !SIM_CODE_PATTERN.matcher(request.getVehicleDeviceCode()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "vehicleDeviceCode must match ^sim_\\d{3}$");
        }
        validateCoordinate("longitude", request.getLongitude(), -180, 180);
        validateCoordinate("latitude", request.getLatitude(), -90, 90);
    }

    private void validateDestination(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "transport task destination coordinates are incomplete");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "transport task destination coordinates are outside the valid range");
        }
    }

    private void validateCoordinate(String name, Double value,
                                    double minimum, double maximum) {
        if (value == null || !Double.isFinite(value)
                || value < minimum || value > maximum) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    name + " is outside the valid range");
        }
    }

    private double distanceMeters(double firstLatitude, double firstLongitude,
                                  double secondLatitude, double secondLongitude) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double firstLatitudeRadians = Math.toRadians(firstLatitude);
        double secondLatitudeRadians = Math.toRadians(secondLatitude);
        double value = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(firstLatitudeRadians) * Math.cos(secondLatitudeRadians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double bounded = Math.min(1.0, Math.max(0.0, value));
        return 2 * EARTH_RADIUS_METERS
                * Math.atan2(Math.sqrt(bounded), Math.sqrt(1 - bounded));
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
