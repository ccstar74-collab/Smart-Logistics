package com.smart_logistics.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackPointResponse {
    private Double lat;
    private Double lon;
    private Double speed;
    private Double heading;
    private OffsetDateTime timestamp;
}