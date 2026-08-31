package com.smart_logistics.backend.service.route;

import com.smart_logistics.backend.dto.InitialRouteScoreDetails;
import com.smart_logistics.backend.dto.WeatherSnapshot;
import com.smart_logistics.backend.enums.TrafficLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InitialRouteScoringService {

    public static final String RULE_VERSION = "initial-route-score-v1";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TIME_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal DISTANCE_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal TRAFFIC_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal WEATHER_WEIGHT = new BigDecimal("0.10");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    public List<ScoredInitialRoute> score(
            List<InitialRouteCandidateGenerator.GeneratedInitialRoute> candidates,
            WeatherSnapshot weather) {
        long shortestDuration = candidates.stream()
                .mapToLong(candidate -> candidate.route().referenceDuration().toSeconds())
                .min().orElseThrow();
        long shortestDistance = candidates.stream()
                .mapToLong(candidate -> candidate.route().distanceMeters())
                .min().orElseThrow();
        BigDecimal weatherScore = weatherScore(weather);

        List<ScoredInitialRoute> scored = new ArrayList<>();
        for (InitialRouteCandidateGenerator.GeneratedInitialRoute candidate : candidates) {
            BigDecimal time = ratioScore(shortestDuration,
                    candidate.route().referenceDuration().toSeconds());
            BigDecimal distance = ratioScore(shortestDistance,
                    candidate.route().distanceMeters());
            BigDecimal traffic = trafficScore(candidate.trafficLevel());
            InitialRouteScoreDetails details = new InitialRouteScoreDetails(
                    time, distance, traffic, weatherScore);
            BigDecimal total = time.multiply(TIME_WEIGHT)
                    .add(distance.multiply(DISTANCE_WEIGHT))
                    .add(traffic.multiply(TRAFFIC_WEIGHT))
                    .add(weatherScore.multiply(WEATHER_WEIGHT))
                    .setScale(2, RoundingMode.HALF_UP);
            scored.add(new ScoredInitialRoute(
                    candidate.previewRouteId(), candidate.route(),
                    candidate.trafficLevel(), details, total, 0, List.of()));
        }

        scored.sort(Comparator
                .comparing(ScoredInitialRoute::totalScore).reversed()
                .thenComparingLong(value -> value.route()
                        .referenceDuration().toSeconds())
                .thenComparingLong(value -> value.route().distanceMeters())
                .thenComparing(ScoredInitialRoute::previewRouteId));

        List<ScoredInitialRoute> ranked = new ArrayList<>();
        for (int index = 0; index < scored.size(); index++) {
            ScoredInitialRoute current = scored.get(index);
            ranked.add(new ScoredInitialRoute(
                    current.previewRouteId(), current.route(), current.trafficLevel(),
                    current.scoreDetails(), current.totalScore(), index + 1,
                    reasons(current, shortestDuration, shortestDistance, index + 1)));
        }
        return List.copyOf(ranked);
    }

    private List<String> reasons(ScoredInitialRoute route,
                                 long shortestDuration,
                                 long shortestDistance,
                                 int rank) {
        List<String> reasons = new ArrayList<>();
        if (rank == 1) {
            reasons.add("综合评分最高");
        }
        if (route.route().referenceDuration().toSeconds() == shortestDuration) {
            reasons.add("预计用时最短");
        }
        if (route.route().distanceMeters() == shortestDistance) {
            reasons.add("路线距离最短");
        }
        reasons.add(switch (route.trafficLevel()) {
            case FREE_FLOW -> "整体交通较为畅通";
            case SLOW -> "存在少量缓行路段";
            case CONGESTED -> "存在一定拥堵路段";
            case SEVERE -> "存在严重拥堵路段";
            case UNKNOWN -> "部分交通数据暂不可用";
        });
        return List.copyOf(reasons.stream().limit(3).toList());
    }

    private BigDecimal ratioScore(long minimum, long current) {
        return BigDecimal.valueOf(minimum)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(current), 2, RoundingMode.HALF_UP)
                .min(HUNDRED)
                .max(BigDecimal.ZERO);
    }

    private BigDecimal trafficScore(TrafficLevel level) {
        return BigDecimal.valueOf(switch (level) {
            case FREE_FLOW -> 100;
            case SLOW -> 75;
            case CONGESTED -> 45;
            case SEVERE -> 20;
            case UNKNOWN -> 60;
        }).setScale(2);
    }

    private BigDecimal weatherScore(WeatherSnapshot snapshot) {
        String weather = snapshot == null || snapshot.weather() == null
                ? "UNKNOWN" : snapshot.weather().toUpperCase(Locale.ROOT);
        int base = classifyWeather(weather);
        int windPower = maximumWindPower(snapshot == null ? null : snapshot.windPower());
        int correction = windPower >= 6 ? 15 : windPower >= 4 ? 5 : 0;
        return BigDecimal.valueOf(Math.max(0, base - correction)).setScale(2);
    }

    private int classifyWeather(String weather) {
        if (containsAny(weather, "暴雨", "暴雪", "冰雹", "雷暴")) return 20;
        if (containsAny(weather, "大雨", "大雪", "强风")) return 45;
        if (containsAny(weather, "轻雾")) return 90;
        if (containsAny(weather, "中雨", "中雪", "雾")) return 65;
        if (containsAny(weather, "小雨", "小雪")) return 80;
        if (containsAny(weather, "阴")) return 90;
        if (containsAny(weather, "晴", "少云", "多云")) return 100;
        return 60;
    }

    private boolean containsAny(String value, String... options) {
        for (String option : options) {
            if (value.contains(option)) return true;
        }
        return false;
    }

    private int maximumWindPower(String windPower) {
        if (windPower == null) return 0;
        Matcher matcher = NUMBER_PATTERN.matcher(windPower);
        int maximum = 0;
        while (matcher.find()) {
            maximum = Math.max(maximum, Integer.parseInt(matcher.group(1)));
        }
        return maximum;
    }

    public record ScoredInitialRoute(String previewRouteId,
                                     com.smart_logistics.backend.service.eta.EtaPlannedRoute route,
                                     TrafficLevel trafficLevel,
                                     InitialRouteScoreDetails scoreDetails,
                                     BigDecimal totalScore,
                                     int rank,
                                     List<String> reasons) {
    }
}
