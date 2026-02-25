package com.example.rideapp.repository;

import com.example.rideapp.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverRepository  extends JpaRepository<Driver, UUID> {
}
