package com.example.rideapp.config;

import com.example.rideapp.entity.Driver;
import com.example.rideapp.repository.DriverRepository;
import com.example.rideapp.service.GeoLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DriverRepository driverRepository;
    private final GeoLocationService geoLocationService;

    @Override
    public void run(String... args) {
        log.info("Initializing test data...");

        // Check if drivers already exist
        if (driverRepository.count() > 0) {
            log.info("Drivers already exist in database, skipping initialization");
            return;
        }

        // Create test drivers near Bangalore (pickup location: 12.9716, 77.5946)
        Driver driver1 = Driver.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                .name("Raj Kumar")
                .rating(4.8)
                .vehicleTier("economy")
                .currentLat(12.9716)
                .currentLong(77.5946)
                .available(true)
                .build();

        Driver driver2 = Driver.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
                .name("Priya Singh")
                .rating(4.9)
                .vehicleTier("premium")
                .currentLat(12.9756)
                .currentLong(77.5982)
                .available(true)
                .build();

        Driver driver3 = Driver.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"))
                .name("Amit Patel")
                .rating(4.5)
                .vehicleTier("economy")
                .currentLat(12.9680)
                .currentLong(77.5900)
                .available(true)
                .build();

        Driver driver4 = Driver.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440004"))
                .name("Neha Gupta")
                .rating(4.7)
                .vehicleTier("premium")
                .currentLat(12.9800)
                .currentLong(77.6050)
                .available(true)
                .build();

        // Save drivers to database
        driverRepository.save(driver1);
        driverRepository.save(driver2);
        driverRepository.save(driver3);
        driverRepository.save(driver4);

        log.info("Saved 4 test drivers to database");

        // Add their locations to Redis geo-index
        try {
            geoLocationService.updateDriverLocation(driver1.getId(), driver1.getCurrentLat(), driver1.getCurrentLong());
            geoLocationService.updateDriverLocation(driver2.getId(), driver2.getCurrentLat(), driver2.getCurrentLong());
            geoLocationService.updateDriverLocation(driver3.getId(), driver3.getCurrentLat(), driver3.getCurrentLong());
            geoLocationService.updateDriverLocation(driver4.getId(), driver4.getCurrentLat(), driver4.getCurrentLong());
            log.info("Successfully added driver locations to Redis");
        } catch (Exception e) {
            log.error("Failed to add driver locations to Redis: {}", e.getMessage());
        }

        log.info("Data initialization completed");
    }
}

