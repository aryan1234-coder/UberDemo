package com.example.rideapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverLocationUpdateResponseDto {

    private UUID driverId;
    private String status;              // e.g., "UPDATED", "IGNORED", "ERROR"
    private String message;             // human-readable, e.g., "Location updated successfully"
    private Instant receivedAt;
    @NotNull
    @Min(-90) @Max(90)// server timestamp when update was received
    private Double lat;
    @NotNull
    @Min(-90) @Max(90)// echo back for confirmation
    private Double lon;

    // Optional: useful for debugging / observability
    private String traceId;             // if using Sleuth/Micrometer tracing
}