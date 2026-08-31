package com.smart_logistics.backend.dto.response;

import com.smart_logistics.backend.dto.InitialRouteScoreDetails;
import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.enums.TrafficLevel;

import java.math.BigDecimal;
import java.util.List;

public record InitialRouteCandidateResponse(String routeId,
                                            String displayName,
                                            int rank,
                                            BigDecimal totalScore,
                                            long distanceMeters,
                                            long referenceDurationSeconds,
                                            TrafficLevel trafficLevel,
                                            String trafficDataSource,
                                            String provider,
                                            String coordinateSystem,
                                            List<List<Double>> points,
                                            TrafficSnapshot traffic,
                                            WeatherSnapshot weather,
                                            InitialRouteScoreDetails scoreDetails,
                                            List<String> reasons) {

    public InitialRouteCandidateResponse {
        points = points.stream().map(List::copyOf).toList();
        reasons = List.copyOf(reasons);
    }
}
