package com.example.rideapp.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class TripStatusDto {
    private String newState;  // "ARRIVED_AT_PICKUP", "STARTED", "ENDED", "PAUSED"
    private String notes;     // optional
    private Instant timestamp = Instant.now();
}
