package com.example.rideapp.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class RideCancelDto {
    private UUID riderId;
    private String reason;
}
