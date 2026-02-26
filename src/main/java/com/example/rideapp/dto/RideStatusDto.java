package com.example.rideapp.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RideStatusDto {
    private UUID rideId;
    private String currentState;  // REQUESTED, ASSIGNED, EN_ROUTE_TO_PICKUP, IN_PROGRESS, COMPLETED, CANCELLED
    private UUID driverId;
    private Integer etaSeconds;
    private String surgeApplied;
}
