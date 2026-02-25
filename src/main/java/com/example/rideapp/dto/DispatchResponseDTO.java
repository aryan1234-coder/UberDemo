package com.example.rideapp.dto;

import jakarta.persistence.Id;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DispatchResponseDTO {
    private UUID tripId;
    private UUID driverId;
    private String driverName;
    private Integer etaMinutes;
    private String status;
}
