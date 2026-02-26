package com.example.rideapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideActionResponseDto {
    private UUID rideId;
    private String status;      // ACCEPTED, DECLINED, CANCELLED, UPDATED, etc.
    private String message;
    private Instant timestamp;
}
