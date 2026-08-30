package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.service.eta.EtaPlannedRoute;

import java.util.List;

public interface MultiObjectiveRouteProvider {

    List<EtaPlannedRoute> planCandidates(
            double startLongitude, double startLatitude,
            double endLongitude, double endLatitude);
}
