package com.airtribe.smartparking.service;

import com.airtribe.smartparking.dto.TicketResponseDto;
import com.airtribe.smartparking.exception.TicketNotFoundException;
import com.airtribe.smartparking.model.ParkingSpot;
import com.airtribe.smartparking.model.Ticket;
import com.airtribe.smartparking.model.Vehicle;
import com.airtribe.smartparking.model.SpotType;
import com.airtribe.smartparking.model.VehicleType;
import com.airtribe.smartparking.repository.ParkingSpotRepository;
import com.airtribe.smartparking.repository.TicketRepository;
import com.airtribe.smartparking.repository.VehicleRepository;
import com.airtribe.smartparking.strategy.FeeCalculationStrategy;
import com.airtribe.smartparking.strategy.SpotAssignmentStrategy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ParkingServiceImpl implements ParkingService {

    private final ParkingSpotRepository spotRepository;
    private final TicketRepository ticketRepository;
    private final VehicleRepository vehicleRepository;
    private final FeeCalculationStrategy feeCalculationStrategy;
    private final SpotAssignmentStrategy spotAssignmentStrategy;
    private final Clock clock;

    public ParkingServiceImpl(ParkingSpotRepository spotRepository, TicketRepository ticketRepository,
                              VehicleRepository vehicleRepository, FeeCalculationStrategy feeCalculationStrategy,
                              SpotAssignmentStrategy spotAssignmentStrategy, Clock clock) {
        this.spotRepository = spotRepository;
        this.ticketRepository = ticketRepository;
        this.vehicleRepository = vehicleRepository;
        this.feeCalculationStrategy = feeCalculationStrategy;
        this.spotAssignmentStrategy = spotAssignmentStrategy;
        this.clock = clock;
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 150, multiplier = 1.5, random = true)
    )
    public TicketResponseDto checkIn(String licensePlate, VehicleType vehicleType) {
        if (ticketRepository.existsByActivePlate(licensePlate)) {
            throw new IllegalStateException("Vehicle with license plate " + licensePlate + " is already parked.");
        }

        Vehicle vehicle = vehicleRepository.findByLicensePlate(licensePlate)
                .map(existing -> {
                    if (existing.getVehicleType() != vehicleType) {
                        existing.setVehicleType(vehicleType);
                        return vehicleRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> vehicleRepository.save(Vehicle.builder()
                        .licensePlate(licensePlate)
                        .vehicleType(vehicleType)
                        .build()));

        ParkingSpot spot = spotAssignmentStrategy.assignSpot(vehicleType, spotRepository);
        spot.setAvailable(false);
        spotRepository.save(spot);

        Ticket ticket = Ticket.builder()
                .ticketNumber("TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .vehicle(vehicle)
                .activePlate(vehicle.getLicensePlate())
                .entryTime(LocalDateTime.now(clock))
                .parkingSpot(spot)
                .build();

        ticketRepository.save(ticket);
        return mapToDto(ticket);
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 150, multiplier = 1.5, random = true)
    )
    public TicketResponseDto checkOut(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketNumber));

        if (ticket.getExitTime() != null) {
            return mapToDto(ticket);
        }

        LocalDateTime exitTime = LocalDateTime.now(clock);
        BigDecimal fee = feeCalculationStrategy.calculateFee(ticket, exitTime);

        ticket.setExitTime(exitTime);
        ticket.setFee(fee);
        ticket.setActivePlate(null);
        ticketRepository.save(ticket);

        ParkingSpot spot = ticket.getParkingSpot();
        spot.setAvailable(true);
        spotRepository.save(spot);

        return mapToDto(ticket);
    }

    @Override
    public Map<String, Object> getAvailability() {
        List<Integer> allFloors = spotRepository.findAllDistinctFloors();
        List<Object[]> results = spotRepository.countAvailableSpotsByFloorAndType();
        Map<String, Object> availability = new LinkedHashMap<>();

        for (Integer floor : allFloors) {
            Map<String, Long> types = new LinkedHashMap<>();
            for (SpotType type : SpotType.values()) {
                types.put(type.name(), 0L);
            }
            availability.put("Floor " + floor, types);
        }

        for (Object[] result : results) {
            Integer floor = (Integer) result[0];
            SpotType type = (SpotType) result[1];
            Long count = (Long) result[2];

            @SuppressWarnings("unchecked")
            Map<String, Long> floorMap = (Map<String, Long>) availability.get("Floor " + floor);
            if (floorMap != null) {
                floorMap.put(type.name(), count);
            }
        }
        return availability;
    }

    private TicketResponseDto mapToDto(Ticket ticket) {
        return TicketResponseDto.builder()
                .ticketNumber(ticket.getTicketNumber())
                .licensePlate(ticket.getVehicle().getLicensePlate())
                .vehicleType(ticket.getVehicle().getVehicleType())
                .floor(ticket.getParkingSpot().getFloor())
                .spotNumber(ticket.getParkingSpot().getSpotNumber())
                .entryTime(ticket.getEntryTime())
                .exitTime(ticket.getExitTime())
                .fee(ticket.getFee())
                .build();
    }
}