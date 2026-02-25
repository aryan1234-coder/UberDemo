package com.example.rideapp.service;

import com.example.rideapp.dto.RideRequestDTO;
import com.example.rideapp.dto.DispatchResponseDTO;
import com.example.rideapp.entity.Assignment;
import com.example.rideapp.entity.Driver;
import com.example.rideapp.entity.RideRequest;
import com.example.rideapp.repository.AssignmentRepository;
import com.example.rideapp.repository.DriverRepository;
import com.example.rideapp.repository.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchService {

    private final RideRequestRepository rideRequestRepo;
    private final DriverRepository driverRepo;
    private final AssignmentRepository assignmentRepo;
    private final GeoLocationService geoService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate; // for idempotency

    private static final String IDEMPOTENCY_PREFIX = "req:";

    public DispatchResponseDTO requestRide(RideRequestDTO dto) {
        log.info("Incoming ride request: riderId={} requestId={}", dto.getRiderId(), dto.getRequestId());

        // Idempotency check (defensive - Redis may be down)
        String key = IDEMPOTENCY_PREFIX + dto.getRequestId();
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                throw new IllegalStateException("Request already processed");
            }
            redisTemplate.opsForValue().set(key, "PROCESSED", 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis idempotency check failed; proceeding without idempotency: {}", e.getMessage());
        }

        // Save request
        RideRequest request = RideRequest.builder()
                .riderId(dto.getRiderId())
                .pickupLat(dto.getPickupLat())
                .pickupLong(dto.getPickupLong())
                .destLat(dto.getDestLat())
                .destLong(dto.getDestLong())
                .tier(dto.getTier())
                .paymentMethod(dto.getPaymentMethod())
                .build();
        try {
            request = rideRequestRepo.save(request);
        } catch (Exception e) {
            log.error("Failed to save RideRequest (DB error). riderId={} requestId={} error={}", dto.getRiderId(), dto.getRequestId(), e.getMessage());
            throw new IllegalStateException("Database error while saving ride request: " + e.getMessage(), e);
        }

        // Find nearest available drivers
        List<NearbyDriver> candidates = (List<NearbyDriver>) geoService.findNearbyDrivers(
                dto.getPickupLat(), dto.getPickupLong(), 5.0);

        if (candidates == null || candidates.isEmpty()) {
            request.setStatus("NO_DRIVERS");
            try {
                rideRequestRepo.save(request);
            } catch (Exception e) {
                log.warn("Failed saving NO_DRIVERS status: {}", e.getMessage());
            }
            throw new IllegalStateException("No drivers nearby");
        }

        // Get real Driver entities + rating (simple ranking: distance * 0.6 + (5-rating)*0.4)
        Optional<Driver> bestDriverOpt = candidates.stream()
                .map(nd -> driverRepo.findById(nd.driverId()).orElse(null))
                .filter(d -> d != null && d.isAvailable())
                .min(Comparator.comparingDouble(d -> {
                    NearbyDriver nd = candidates.stream()
                            .filter(c -> c.driverId().equals(d.getId()))
                            .findFirst().orElseThrow();
                    return nd.distanceKm() * 0.6 + (5 - d.getRating()) * 0.4;
                }));

        Driver bestDriver = bestDriverOpt.orElseThrow(() -> new IllegalStateException("No available drivers"));

        // Assign
        Assignment assignment = Assignment.builder()
                .rideRequest(request)
                .driver(bestDriver)
                .estimatedMinutes(estimateEta(bestDriver, dto))
                .build();
        try {
            assignmentRepo.save(assignment);
        } catch (Exception e) {
            log.error("Failed to save Assignment. tripId={} driverId={} error={}", request.getId(), bestDriver.getId(), e.getMessage());
            throw new IllegalStateException("Database error while saving assignment: " + e.getMessage(), e);
        }

        bestDriver.setAvailable(false);
        try {
            driverRepo.save(bestDriver);
        } catch (Exception e) {
            log.error("Failed to update Driver availability. driverId={} error={}", bestDriver.getId(), e.getMessage());
            // proceed; not critical to fail the entire request
        }

        request.setStatus("ASSIGNED");
        try {
            rideRequestRepo.save(request);
        } catch (Exception e) {
            log.warn("Failed to update ride status to ASSIGNED: {}", e.getMessage());
        }

        // Publish event
        try {
            kafkaTemplate.send("driver-assigned", Map.of(
                    "tripId", request.getId(),
                    "driverId", bestDriver.getId(),
                    "eta", assignment.getEstimatedMinutes()
            ));
        } catch (Exception e) {
            log.warn("Failed to publish Kafka event driver-assigned: {}", e.getMessage());
        }

        return DispatchResponseDTO.builder()
                .tripId(request.getId())
                .driverId(bestDriver.getId())
                .driverName(bestDriver.getName())
                .etaMinutes(assignment.getEstimatedMinutes())
                .status("ASSIGNED")
                .build();
    }

    private int estimateEta(Driver driver, RideRequestDTO dto) {
        // Defensive: if driver's stored coordinates are missing, return a safe default ETA
        if (driver.getCurrentLat() == null || driver.getCurrentLong() == null) {
            return 5; // assume 5 minutes if we don't have the driver's precise location
        }

        // Very simple haversine + traffic factor
        double distance = haversine(driver.getCurrentLat(), driver.getCurrentLong(),
                dto.getPickupLat(), dto.getPickupLong());
        return (int) (distance / 0.8) + 2; // 48 km/h avg + 2 min pickup
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        // implementation omitted for brevity (standard formula)
        return 2.5; // placeholder
    }
}
