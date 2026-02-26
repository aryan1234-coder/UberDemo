package com.example.rideapp.controller;

import com.example.rideapp.dto.*;
import com.example.rideapp.repository.RideRequestRepository;
import com.example.rideapp.service.DispatchService;
import com.example.rideapp.service.GeoLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
@Slf4j
@Validated
public class DispatchController {

    private final DispatchService dispatchService;
    private final GeoLocationService geoService;
    private final RideRequestRepository rideRequestRepo; // for quick status checks if needed

    // ────────────────────────────────────────────────
    // Driver-side endpoints (frequent, low-latency)
    // ────────────────────────────────────────────────

    /**
     * Driver sends location update (1-2 per second when online)
     * Idempotent via timestamp + driverId (optional short TTL dedup in Redis)
     */
    @PostMapping("/driver/location")
    public ResponseEntity<DriverLocationUpdateResponseDto> updateDriverLocation(
            @Valid @RequestBody DriverLocationUpdateResponseDto dto) {

        geoService.updateDriverLocation(dto.getDriverId(), dto.getLat(), dto.getLon());

        // Optional: publish Kafka event "driver-location-updated" for analytics / heatmaps

        return ResponseEntity.ok(DriverLocationUpdateResponseDto.builder()
                .driverId(dto.getDriverId())
                .status("UPDATED")
                .message("Location updated")
                .receivedAt(Instant.now())
                .lat(dto.getLat())
                .lon(dto.getLon())
                .build());
    }

    /**
     * Driver accepts an assigned ride
     * Idempotent (same rideId + driverId combo)
     */
    @PostMapping("/ride/{rideId}/accept")
    public ResponseEntity<RideActionResponseDto> acceptRide(
            @PathVariable UUID rideId,
            @Valid @RequestBody RideActionDto dto) {  // can be empty if auth via JWT

        RideActionResponseDto response = dispatchService.acceptRide(rideId, dto.getDriverId());

        return ResponseEntity.ok(response);
    }

    /**
     * Driver declines → triggers re-matching / next driver
     * Idempotent
     */
    @PostMapping("/ride/{rideId}/decline")
    public ResponseEntity<RideActionResponseDto> declineRide(
            @PathVariable UUID rideId,
            @Valid @RequestBody RideDeclineDto dto) {

        RideActionResponseDto response = dispatchService.declineRide(rideId, dto.getDriverId(), dto.getReason());

        return ResponseEntity.ok(response);
    }

    /**
     * Driver updates trip state: arrived, start, end, etc.
     * Idempotent via state + timestamp check
     */
    @PatchMapping("/ride/{rideId}/state")
    public ResponseEntity<RideActionResponseDto> updateTripState(
            @PathVariable UUID rideId,
            @Valid @RequestBody TripStatusDto dto) {

        RideActionResponseDto response = dispatchService.updateTripState(rideId, dto);

        return ResponseEntity.ok(response);
    }

    // ────────────────────────────────────────────────
    // Rider-side endpoints
    // ────────────────────────────────────────────────

    /**
     * Rider requests ride → synchronous matching attempt
     * Idempotent via requestId
     * Returns assigned driver or queued status
     */
    @PostMapping("/ride/request")
    public ResponseEntity<DispatchResponseDTO> requestRide(
            @Valid @RequestBody RideRequestDTO dto) {

        DispatchResponseDTO response = dispatchService.requestRide(dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Rider cancels before driver accepts
     */
    @PostMapping("/ride/{rideId}/cancel")
    public ResponseEntity<RideActionResponseDto> cancelRide(
            @PathVariable UUID rideId,
            @Valid @RequestBody RideCancelDto dto) {

        RideActionResponseDto response = dispatchService.cancelRide(rideId, dto.getRiderId(), dto.getReason());
        return ResponseEntity.ok(response);
    }

    /**
     * Get current ride status (poll fallback if no WebSocket)
     */
    @GetMapping("/ride/{rideId}/status")
    public ResponseEntity<RideStatusDto> getRideStatus(@PathVariable UUID rideId) {
        RideStatusDto status = dispatchService.getRideStatus(rideId);
        return ResponseEntity.ok(status);
    }
}