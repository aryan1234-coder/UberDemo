package com.example.rideapp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DriverLocationService {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY = "drivers";



    public void updateLocation(String driverId, double lat, double lng) {

        redisTemplate.opsForGeo().add(
                KEY,
                new org.springframework.data.geo.Point(lng, lat),
                driverId
        );
    }
}
