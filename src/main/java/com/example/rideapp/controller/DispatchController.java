package com.example.rideapp.controller;

import com.example.rideapp.dto.DispatchResponseDTO;
import com.example.rideapp.dto.DriverLocationUpdateResponseDto;
import com.example.rideapp.dto.RideRequestDTO;
import com.example.rideapp.service.DispatchService;
import com.example.rideapp.service.GeoLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;
    private final GeoLocationService geoService;

    // Driver sends location every 1-2 sec
    @PostMapping("/driver/location")
    public ResponseEntity<String> updateDriverLocation(@RequestBody DriverLocationUpdateResponseDto dto) {
        geoService.updateDriverLocation(dto.getDriverId(), dto.getLat(), dto.getLon());
        return ResponseEntity.ok("Location updated");
    }

    // Rider requests ride
    @PostMapping("/ride/request")
    public ResponseEntity<DispatchResponseDTO> requestRide(@Valid @RequestBody RideRequestDTO dto) {
        DispatchResponseDTO response = dispatchService.requestRide(dto);
        return ResponseEntity.ok(response);
    }
}