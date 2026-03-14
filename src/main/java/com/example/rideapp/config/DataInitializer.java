package com.example.rideapp.config;

import com.example.rideapp.entity.Driver;
import com.example.rideapp.repository.DriverRepository;
import com.example.rideapp.service.GeoLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
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

        // ── Step 1: Save drivers to DB only on first run ──────────────────────
        if (driverRepository.count() == 0) {

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

            Driver driver5 = Driver.builder()
                    .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440005"))
                    .name("Suresh Reddy")
                    .rating(4.6)
                    .vehicleTier("economy")
                    .currentLat(12.9650)
                    .currentLong(77.6100)
                    .available(true)
                    .build();

            Driver driver6 = Driver.builder()
                    .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440006"))
                    .name("Anjali Mehta")
                    .rating(4.9)
                    .vehicleTier("premium")
                    .currentLat(12.9700)
                    .currentLong(77.6200)
                    .available(true)
                    .build();


            // Fixed: was 13.97800 / 78.60300 (wrong — 133 km away near Tirupati)
            Driver driver7 = Driver.builder()
                    .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440007"))
                    .name("Vikram Singh")
                    .rating(4.4)
                    .vehicleTier("economy")
                    .currentLat(12.9780)
                    .currentLong(77.6030)
                    .available(true)
                    .build();

            Driver driver8 = Driver.builder()
                    .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440008"))
                    .name("Sonal Sharma")
                    .rating(4.8)
                    .vehicleTier("premium")
                    .currentLat(12.9850)
                    .currentLong(77.6150)
                    .available(true)
                    .build();

            Driver driver9 = Driver.builder()
                    .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440009"))
                    .name("Rohit Verma")
                    .rating(4.5)
                    .vehicleTier("economy")
                    .currentLat(12.9550)
                    .currentLong(77.6250)
                    .available(true)
                    .build();

            // Fixed: UUID was "...4400010" (13 chars in last segment — invalid)
            Driver driver10 = Driver.builder()
                    .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440010"))
                    .name("Rohit Kumar")
                    .rating(4.7)
                    .vehicleTier("economy")
                    .currentLat(12.9553)
                    .currentLong(77.6930)
                    .available(true)
                    .build();

            driverRepository.save(driver1);
            driverRepository.save(driver2);
            driverRepository.save(driver3);
            driverRepository.save(driver4);
            driverRepository.save(driver5);
            driverRepository.save(driver6);
            driverRepository.save(driver7);
            driverRepository.save(driver8);
            driverRepository.save(driver9);
            driverRepository.save(driver10);

            log.info("Saved 10 drivers to database");

        } else {
            log.info("Drivers already exist in database, skipping DB save");
        }

        // ── Step 2: ALWAYS push ALL drivers to Redis on every startup ─────────
        // Redis is in-memory — it loses all data when it restarts.
        // PostgreSQL is persistent — drivers survive app restarts.
        // So we must always reload from DB → Redis on every boot.
        try {
            List<Driver> allDrivers = driverRepository.findAll();
            for (Driver driver : allDrivers) {
                geoLocationService.updateDriverLocation(
                        driver.getId(),
                        driver.getCurrentLat(),
                        driver.getCurrentLong()
                );
            }
            log.info("Successfully pushed {} drivers to Redis geo-index", allDrivers.size());
        } catch (Exception e) {
            log.error("Failed to push drivers to Redis geo-index: {}", e.getMessage());
        }

        log.info("Data initialization completed");
    }
}