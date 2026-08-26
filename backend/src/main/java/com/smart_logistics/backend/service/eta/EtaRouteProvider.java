package com.smart_logistics.backend.service.eta;

public interface EtaRouteProvider {

    EtaPlannedRoute plan(double startLongitude, double startLatitude,
                         double endLongitude, double endLatitude);
}
