package com.airtribe.smartparking.controller;

import com.airtribe.smartparking.dto.CheckInRequest;
import com.airtribe.smartparking.dto.TicketResponseDto;
import com.airtribe.smartparking.service.ParkingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/parking")
public class ParkingController {

    private final ParkingService parkingService;

    public ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> getAvailability() {
        return new ResponseEntity<>(parkingService.getAvailability(), HttpStatus.OK);
    }

    @PostMapping("/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(@Valid @RequestBody CheckInRequest request) {
        TicketResponseDto ticket = parkingService.checkIn(request.getLicensePlate(), request.getVehicleType());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Check-in successful. Parking spot allocated.");
        response.put("ticket", ticket);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/check-out/{ticketNumber}")
    public ResponseEntity<Map<String, Object>> checkOut(@PathVariable String ticketNumber) {
        TicketResponseDto ticket = parkingService.checkOut(ticketNumber);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Checkout successful. Parking spot is now available.");
        response.put("ticket", ticket);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}