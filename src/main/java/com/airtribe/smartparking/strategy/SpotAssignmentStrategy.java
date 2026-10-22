package com.airtribe.smartparking.strategy;

import com.airtribe.smartparking.model.ParkingSpot;
import com.airtribe.smartparking.model.VehicleType;
import com.airtribe.smartparking.repository.ParkingSpotRepository;

public interface SpotAssignmentStrategy {
    ParkingSpot assignSpot(VehicleType vehicleType, ParkingSpotRepository spotRepository);
}