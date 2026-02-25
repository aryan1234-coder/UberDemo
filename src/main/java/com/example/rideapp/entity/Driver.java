package com.example.rideapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Driver {
    @Id
    private UUID id = UUID.randomUUID();

    private String name;
    private Double rating = 5.0;
    private String vehicleTier; // economy, premium

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_long")
    private Double currentLong;

    private boolean available = true;
}