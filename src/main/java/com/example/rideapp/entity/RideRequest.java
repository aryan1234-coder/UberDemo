package com.example.rideapp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ride_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID riderId;
    private Double pickupLat, pickupLong;
    private Double destLat, destLong;
    private String tier;
    private String paymentMethod;
    private String status = "REQUESTED"; // REQUESTED, ASSIGNED, COMPLETED, CANCELLED
}