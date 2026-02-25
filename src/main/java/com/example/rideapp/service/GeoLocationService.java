package com.example.rideapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeoLocationService {

    private final GeoOperations<String, String> geoOperations;
    private static final String KEY = "driver:locations";

    public void updateDriverLocation(UUID driverId, Double lat, Double lon) {
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid coordinates: lat must be [-90,90], lon [-180,180]");
        }

        try {
            Point point = new Point(lon, lat);           // Correct: x=lon, y=lat
            geoOperations.add(KEY, point, driverId.toString());
            log.debug("Updated driver location: driverId={} lat={} lon={}", driverId, lat, lon);
        } catch (Exception e) {
            log.error("Failed to update driver location in Redis: {}", e.getMessage());
            throw new IllegalStateException("Redis connection failed: " + e.getMessage(), e);
        }
    }

    public List<NearbyDriver> findNearbyDrivers(Double lat, Double lon, double radiusKm) {
        try {
            Circle circle = new Circle(new Point(lon, lat), new Distance(radiusKm, Metrics.KILOMETERS));

            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs()
                    .includeCoordinates()
                    .includeDistance()
                    .sortAscending()
                    .limit(20);

            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    geoOperations.radius(KEY, circle, args);

            if (results == null || results.getContent().isEmpty()) {
                log.info("No nearby drivers found for location lat={} lon={} within {}km", lat, lon, radiusKm);
                return Collections.emptyList();
            }

            List<NearbyDriver> nearbyDrivers = results.getContent().stream()
                    .map(r -> new NearbyDriver(
                            UUID.fromString(r.getContent().getName()),
                            r.getDistance().getValue(),
                            r.getContent().getPoint().getY(),
                            r.getContent().getPoint().getX()))
                    .toList();

            log.info("Found {} nearby drivers for location lat={} lon={}", nearbyDrivers.size(), lat, lon);
            return nearbyDrivers;
        } catch (Exception e) {
            log.error("Redis geo lookup failed: {}", e.getMessage());
            throw new IllegalStateException("Redis connection failed: " + e.getMessage(), e);
        }
    }
}

record NearbyDriver(UUID driverId, double distanceKm, double lat, double lon) {}
