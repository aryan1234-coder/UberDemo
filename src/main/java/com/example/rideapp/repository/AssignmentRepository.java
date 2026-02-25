package com.example.rideapp.repository;

import com.example.rideapp.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
}
