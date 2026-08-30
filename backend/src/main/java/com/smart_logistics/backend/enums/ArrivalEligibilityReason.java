package com.smart_logistics.backend.enums;

public enum ArrivalEligibilityReason {
    ARRIVAL_ALLOWED,
    TASK_NOT_TRANSPORTING,
    DESTINATION_COORDINATES_MISSING,
    LOCATION_NOT_FOUND,
    LOCATION_OFFLINE,
    OUTSIDE_GEOFENCE
}
