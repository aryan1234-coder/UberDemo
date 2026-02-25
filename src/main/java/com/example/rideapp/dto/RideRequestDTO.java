package com.example.rideapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestDTO {
    @NotNull
    private UUID riderId;
    @NotNull private Double pickupLat, pickupLong;
    @NotNull private Double destLat, destLong;
    private String tier = "economy";
    private String paymentMethod = "card";
    @NotBlank
    private String requestId; // for idempotency
}
