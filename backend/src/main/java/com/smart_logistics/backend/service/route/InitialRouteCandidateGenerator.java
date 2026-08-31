package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.enums.TrafficLevel;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaProviderException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class InitialRouteCandidateGenerator {

    private static final int MINIMUM_CANDIDATE_COUNT = 2;
    private static final int MAXIMUM_CANDIDATE_COUNT = 3;
    private static final int SIMILARITY_SAMPLE_COUNT = 20;
    private static final double SIMILAR_POINT_DISTANCE_METERS = 50.0;
    private static final double SIMILARITY_THRESHOLD = 0.95;

    private final MultiObjectiveRouteProvider routeProvider;
    private final TrafficLevelClassifier trafficLevelClassifier;

    public InitialRouteCandidateGenerator(
            MultiObjectiveRouteProvider routeProvider,
            TrafficLevelClassifier trafficLevelClassifier) {
        this.routeProvider = routeProvider;
        this.trafficLevelClassifier = trafficLevelClassifier;
    }

    public List<GeneratedInitialRoute> generate(
            double startLongitude, double startLatitude,
            double endLongitude, double endLatitude,
            int candidateCount) {
        if (candidateCount < MINIMUM_CANDIDATE_COUNT
                || candidateCount > MAXIMUM_CANDIDATE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "candidateCount must be between 2 and 3");
        }
        if (sameCoordinate(startLongitude, startLatitude,
                endLongitude, endLatitude)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "route start and destination must be different");
        }

        List<EtaPlannedRoute> providerRoutes;
        try {
            providerRoutes = routeProvider.planCandidates(
                    startLongitude, startLatitude, endLongitude, endLatitude);
        } catch (EtaProviderException exception) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "initial route candidates are unavailable: "
                            + exception.getMessage());
        }

        List<EtaPlannedRoute> distinct = new ArrayList<>();
        for (EtaPlannedRoute route : providerRoutes) {
            if (distinct.stream().noneMatch(existing -> sameRoute(existing, route))) {
                distinct.add(route);
            }
            if (distinct.size() == candidateCount) {
                break;
            }
        }
        if (distinct.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new BusinessException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                    "route provider returned fewer than two distinct candidates");
        }

        return distinct.stream()
                .map(route -> new GeneratedInitialRoute(
                        stablePreviewRouteId(route), route,
                        trafficLevelClassifier.classify(route.trafficSnapshot())))
                .toList();
    }

    private boolean sameRoute(EtaPlannedRoute left, EtaPlannedRoute right) {
        if (relativeDifference(left.distanceMeters(), right.distanceMeters()) >= 0.01
                || relativeDifference(left.referenceDuration().toSeconds(),
                right.referenceDuration().toSeconds()) >= 0.01) {
            return false;
        }
        return sampledGeometrySimilarity(left.polyline(), right.polyline()) >
                SIMILARITY_THRESHOLD;
    }

    private double sampledGeometrySimilarity(
            List<EtaCoordinate> left, List<EtaCoordinate> right) {
        int matched = 0;
        for (int index = 0; index < SIMILARITY_SAMPLE_COUNT; index++) {
            EtaCoordinate leftPoint = sampledPoint(left, index);
            EtaCoordinate rightPoint = sampledPoint(right, index);
            if (distanceMeters(leftPoint, rightPoint)
                    <= SIMILAR_POINT_DISTANCE_METERS) {
                matched++;
            }
        }
        return (double) matched / SIMILARITY_SAMPLE_COUNT;
    }

    private EtaCoordinate sampledPoint(List<EtaCoordinate> points, int sampleIndex) {
        int index = (int) Math.round((double) sampleIndex * (points.size() - 1)
                / (SIMILARITY_SAMPLE_COUNT - 1));
        return points.get(index);
    }

    private double relativeDifference(long left, long right) {
        return Math.abs((double) left - right) / Math.max(left, right);
    }

    private boolean sameCoordinate(double startLongitude, double startLatitude,
                                   double endLongitude, double endLatitude) {
        return Math.abs(startLongitude - endLongitude) < 0.000001
                && Math.abs(startLatitude - endLatitude) < 0.000001;
    }

    private double distanceMeters(EtaCoordinate left, EtaCoordinate right) {
        double earthRadius = 6_371_000.0;
        double latitudeDelta = Math.toRadians(right.latitude() - left.latitude());
        double longitudeDelta = Math.toRadians(right.longitude() - left.longitude());
        double sinLatitude = Math.sin(latitudeDelta / 2.0);
        double sinLongitude = Math.sin(longitudeDelta / 2.0);
        double value = sinLatitude * sinLatitude
                + Math.cos(Math.toRadians(left.latitude()))
                * Math.cos(Math.toRadians(right.latitude()))
                * sinLongitude * sinLongitude;
        return earthRadius * 2.0 * Math.atan2(Math.sqrt(value), Math.sqrt(1.0 - value));
    }

    private String stablePreviewRouteId(EtaPlannedRoute route) {
        StringBuilder fingerprint = new StringBuilder()
                .append(route.distanceMeters()).append('|')
                .append(route.referenceDuration().toSeconds());
        for (EtaCoordinate point : route.polyline()) {
            fingerprint.append('|').append(String.format(Locale.ROOT,
                    "%.6f,%.6f", point.longitude(), point.latitude()));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8));
            return "preview_route_" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record GeneratedInitialRoute(String previewRouteId,
                                        EtaPlannedRoute route,
                                        TrafficLevel trafficLevel) {
    }
}
