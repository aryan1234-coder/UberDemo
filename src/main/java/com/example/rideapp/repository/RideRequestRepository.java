package com.example.rideapp.repository;

import com.example.rideapp.entity.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RideRequestRepository extends JpaRepository<RideRequest, UUID> {
}
