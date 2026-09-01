package com.smart_logistics.backend.dto;

import java.math.BigDecimal;

public record InitialRouteScoreDetails(BigDecimal time,
                                       BigDecimal distance,
                                       BigDecimal traffic,
                                       BigDecimal weather) {
}
