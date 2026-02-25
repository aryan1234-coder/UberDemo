package com.example.rideapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "ride_request_id")
    private RideRequest rideRequest;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;

    private Integer estimatedMinutes;
    private String status = "ASSIGNED";
}