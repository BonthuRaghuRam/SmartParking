package com.airtribe.smartparking.strategy;

import com.airtribe.smartparking.model.ParkingSpot;
import com.airtribe.smartparking.model.SpotType;
import com.airtribe.smartparking.model.VehicleType;
import com.airtribe.smartparking.repository.ParkingSpotRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NearestFirstSpotStrategy implements SpotAssignmentStrategy {

    @Override
    public ParkingSpot assignSpot(VehicleType vehicleType, ParkingSpotRepository repository) {
        // Defines the fallback order: tries the best fit first, then moves to larger spots
        SpotType[] allowedTypes = switch (vehicleType) {
            case MOTORCYCLE -> new SpotType[]{SpotType.SMALL, SpotType.COMPACT, SpotType.LARGE};
            case CAR -> new SpotType[]{SpotType.COMPACT, SpotType.LARGE};
            case BUS -> new SpotType[]{SpotType.LARGE};
        };

        for (SpotType type : allowedTypes) {
            Optional<ParkingSpot> spot = repository.findFirstBySpotTypeAndIsAvailableTrueOrderByFloorAscSpotNumberAsc(type);
            if (spot.isPresent()) {
                return spot.get();
            }
        }
        throw new IllegalStateException("Parking lot is completely full for vehicle type: " + vehicleType);
    }
}