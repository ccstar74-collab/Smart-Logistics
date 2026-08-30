package com.smart_logistics.backend.service;

import com.smart_logistics.backend.dto.response.ArrivalEligibilityResponse;
import com.smart_logistics.backend.dto.response.VehicleLocationResponse;
import com.smart_logistics.backend.entity.TransportTask;
import com.smart_logistics.backend.enums.ArrivalEligibilityReason;
import com.smart_logistics.backend.enums.TransportTaskStatus;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.Wgs84ToGcj02Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ArrivalGeofenceService {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final VehicleLocationQueryService vehicleLocationQueryService;
    private final double arrivalRadiusMeters;

    public ArrivalGeofenceService(
            VehicleLocationQueryService vehicleLocationQueryService,
            @Value("${app.geofence.arrival-radius-meters:200}")
            double arrivalRadiusMeters) {
        if (!Double.isFinite(arrivalRadiusMeters) || arrivalRadiusMeters <= 0) {
            throw new IllegalArgumentException("arrival geofence radius must be positive");
        }
        this.vehicleLocationQueryService = vehicleLocationQueryService;
        this.arrivalRadiusMeters = arrivalRadiusMeters;
    }

    public ArrivalEligibilityResponse evaluate(TransportTask task) {
        if (!TransportTaskStatus.TRANSPORTING.name().equals(task.getStatus())) {
            return unavailable(task, ArrivalEligibilityReason.TASK_NOT_TRANSPORTING);
        }
        if (!validLongitude(task.getEndLongitude()) || !validLatitude(task.getEndLatitude())) {
            return unavailable(task, ArrivalEligibilityReason.DESTINATION_COORDINATES_MISSING);
        }

        VehicleLocationResponse location;
        try {
            location = vehicleLocationQueryService.getLatestLocation(task.getVehicleId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                return unavailable(task, ArrivalEligibilityReason.LOCATION_NOT_FOUND);
            }
            throw exception;
        }
        if (!location.isOnline()) {
            return new ArrivalEligibilityResponse(task.getId(), false, null,
                    arrivalRadiusMeters, location.getCollectedAt(), false,
                    ArrivalEligibilityReason.LOCATION_OFFLINE);
        }

        Wgs84ToGcj02Converter.Coordinate gpsGcj02 =
                Wgs84ToGcj02Converter.convert(
                        location.getLongitude(), location.getLatitude());
        double distanceMeters = distanceMeters(
                gpsGcj02.longitude(), gpsGcj02.latitude(),
                task.getEndLongitude(), task.getEndLatitude());
        double roundedDistance = Math.round(distanceMeters * 10.0) / 10.0;
        boolean eligible = distanceMeters <= arrivalRadiusMeters;
        return new ArrivalEligibilityResponse(task.getId(), eligible, roundedDistance,
                arrivalRadiusMeters, location.getCollectedAt(), true,
                eligible ? ArrivalEligibilityReason.ARRIVAL_ALLOWED
                        : ArrivalEligibilityReason.OUTSIDE_GEOFENCE);
    }

    public void requireArrivalAllowed(TransportTask task) {
        ArrivalEligibilityResponse result = evaluate(task);
        if (result.eligible()) {
            return;
        }
        String message = switch (result.reason()) {
            case TASK_NOT_TRANSPORTING -> "only transporting task can be completed";
            case DESTINATION_COORDINATES_MISSING ->
                    "task destination coordinates are missing";
            case LOCATION_NOT_FOUND ->
                    "vehicle location is unavailable; arrival cannot be confirmed";
            case LOCATION_OFFLINE ->
                    "vehicle latest location is offline; fresh GPS is required";
            case OUTSIDE_GEOFENCE -> String.format(
                    "vehicle is %.1f meters from destination; enter the %.0f-meter "
                            + "arrival geofence before completing the task",
                    result.distanceMeters(), result.radiusMeters());
            case ARRIVAL_ALLOWED -> throw new IllegalStateException(
                    "eligible arrival cannot be rejected");
        };
        throw new BusinessException(ErrorCode.STATE_CONFLICT, message);
    }

    private ArrivalEligibilityResponse unavailable(
            TransportTask task, ArrivalEligibilityReason reason) {
        return new ArrivalEligibilityResponse(task.getId(), false, null,
                arrivalRadiusMeters, null, false, reason);
    }

    private boolean validLongitude(Double value) {
        return value != null && Double.isFinite(value) && value >= -180 && value <= 180;
    }

    private boolean validLatitude(Double value) {
        return value != null && Double.isFinite(value) && value >= -90 && value <= 90;
    }

    private double distanceMeters(double fromLongitude, double fromLatitude,
                                  double toLongitude, double toLatitude) {
        double fromLatitudeRadians = Math.toRadians(fromLatitude);
        double toLatitudeRadians = Math.toRadians(toLatitude);
        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(fromLatitudeRadians) * Math.cos(toLatitudeRadians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        haversine = Math.max(0.0, Math.min(1.0, haversine));
        return 2 * EARTH_RADIUS_METERS
                * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }
}
