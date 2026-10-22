package com.airtribe.smartparking.service;

import com.airtribe.smartparking.dto.TicketResponseDto;
import com.airtribe.smartparking.model.VehicleType;

import java.util.Map;

public interface ParkingService {
    TicketResponseDto checkIn(String licensePlate, VehicleType vehicleType);
    TicketResponseDto checkOut(String ticketNumber);
    Map<String, Object> getAvailability();
}