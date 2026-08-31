package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.enums.TrafficLevel;
import org.springframework.stereotype.Service;

@Service
public class TrafficLevelClassifier {

    public TrafficLevel classify(TrafficSnapshot traffic) {
        if (traffic == null) {
            return TrafficLevel.UNKNOWN;
        }
        long knownDistance = traffic.smoothDistanceMeters()
                + traffic.slowDistanceMeters()
                + traffic.congestedDistanceMeters()
                + traffic.severeCongestedDistanceMeters();
        if (knownDistance <= 0) {
            return TrafficLevel.UNKNOWN;
        }
        double weightedRatio = (traffic.slowDistanceMeters() * 0.4
                + traffic.congestedDistanceMeters() * 0.75
                + traffic.severeCongestedDistanceMeters()) / knownDistance;
        if (weightedRatio >= 0.25) {
            return TrafficLevel.SEVERE;
        }
        if (weightedRatio >= 0.10) {
            return TrafficLevel.CONGESTED;
        }
        if (weightedRatio >= 0.02) {
            return TrafficLevel.SLOW;
        }
        return TrafficLevel.FREE_FLOW;
    }
}
