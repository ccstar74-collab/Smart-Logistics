package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.dto.TrafficSnapshot;
import com.smart_logistics.backend.enums.TrafficLevel;
import com.smart_logistics.backend.exception.BusinessException;
import com.smart_logistics.backend.exception.ErrorCode;
import com.smart_logistics.backend.service.eta.EtaCoordinate;
import com.smart_logistics.backend.service.eta.EtaPlannedRoute;
import com.smart_logistics.backend.service.eta.EtaProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialRouteCandidateGeneratorTest {

    @Mock private MultiObjectiveRouteProvider provider;
    private InitialRouteCandidateGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new InitialRouteCandidateGenerator(
                provider, new TrafficLevelClassifier());
    }

    @Test
    void generatesTaskIndependentDistinctCandidatesWithStablePreviewIds() {
        EtaPlannedRoute first = route(1_000, 100, 29.50);
        EtaPlannedRoute duplicate = route(1_005, 100, 29.50);
        EtaPlannedRoute second = route(1_300, 130, 29.55);
        when(provider.planCandidates(106.50, 29.50, 106.60, 29.60))
                .thenReturn(List.of(first, duplicate, second));

        List<InitialRouteCandidateGenerator.GeneratedInitialRoute> actual =
                generator.generate(106.50, 29.50, 106.60, 29.60, 3);

        assertEquals(2, actual.size());
        assertEquals(TrafficLevel.FREE_FLOW, actual.getFirst().trafficLevel());
        assertNotEquals(actual.get(0).previewRouteId(), actual.get(1).previewRouteId());
        assertEquals(actual.getFirst().previewRouteId(),
                generator.generate(106.50, 29.50, 106.60, 29.60, 3)
                        .getFirst().previewRouteId());
    }

    @Test
    void rejectsProviderResultWithFewerThanTwoDistinctRoutes() {
        EtaPlannedRoute route = route(1_000, 100, 29.50);
        when(provider.planCandidates(106.50, 29.50, 106.60, 29.60))
                .thenReturn(List.of(route, route));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> generator.generate(106.50, 29.50, 106.60, 29.60, 3));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                exception.getErrorCode());
    }

    @Test
    void mapsProviderFailureToPublicServiceUnavailableError() {
        when(provider.planCandidates(106.50, 29.50, 106.60, 29.60))
                .thenThrow(new EtaProviderException("timeout"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> generator.generate(106.50, 29.50, 106.60, 29.60, 2));

        assertEquals(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE,
                exception.getErrorCode());
    }

    @Test
    void rejectsCandidateCountAndEqualEndpointsBeforeProviderCall() {
        assertEquals(ErrorCode.INVALID_PARAMETER,
                assertThrows(BusinessException.class,
                        () -> generator.generate(106.50, 29.50,
                                106.60, 29.60, 1)).getErrorCode());
        assertEquals(ErrorCode.INVALID_PARAMETER,
                assertThrows(BusinessException.class,
                        () -> generator.generate(106.50, 29.50,
                                106.50, 29.50, 2)).getErrorCode());
    }

    private EtaPlannedRoute route(long distance, long seconds,
                                  double middleLatitude) {
        return new EtaPlannedRoute(List.of(
                new EtaCoordinate(106.50, 29.50),
                new EtaCoordinate(106.55, middleLatitude),
                new EtaCoordinate(106.60, 29.60)), distance,
                Duration.ofSeconds(seconds),
                new TrafficSnapshot("AMAP_DRIVING_V3", "11", false,
                        5, 0, distance, 0, 0, 0));
    }
}
