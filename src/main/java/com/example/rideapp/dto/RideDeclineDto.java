package com.example.rideapp.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class RideDeclineDto {

    private UUID driverId;
    private UUID tripId;
    private String reason;
}
