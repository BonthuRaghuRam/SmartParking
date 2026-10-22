package com.airtribe.smartparking.service;

import com.airtribe.smartparking.dto.TicketResponseDto;
import com.airtribe.smartparking.model.ParkingSpot;
import com.airtribe.smartparking.model.SpotType;
import com.airtribe.smartparking.model.VehicleType;
import com.airtribe.smartparking.repository.ParkingSpotRepository;
import com.airtribe.smartparking.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ParkingServiceIntegrationTest {

    @Autowired
    private ParkingService parkingService;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        spotRepository.deleteAll();

        // Seed 2 specific spots
        spotRepository.save(ParkingSpot.builder().floor(1).spotNumber(1).spotType(SpotType.SMALL).isAvailable(true).build());
        spotRepository.save(ParkingSpot.builder().floor(1).spotNumber(2).spotType(SpotType.LARGE).isAvailable(true).build());
    }

    @Test
    void testCheckIn_AssignsCorrectSpotAndLocksIt() {
        TicketResponseDto ticket = parkingService.checkIn("AP09-CAR", VehicleType.CAR);

        assertNotNull(ticket.getTicketNumber());
        assertEquals("AP09-CAR", ticket.getLicensePlate());

        assertEquals(1, ticket.getFloor());
        assertEquals(2, ticket.getSpotNumber());

        ParkingSpot dbSpot = spotRepository.findAll().stream()
                .filter(s -> s.getFloor().equals(ticket.getFloor()) && s.getSpotNumber().equals(ticket.getSpotNumber()))
                .findFirst().orElseThrow();
        assertFalse(dbSpot.isAvailable());
    }



    @Test
    void testCheckIn_RejectsDoubleBooking() {
        parkingService.checkIn("AP09-MOTO", VehicleType.MOTORCYCLE);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            parkingService.checkIn("AP09-MOTO", VehicleType.MOTORCYCLE);
        });

        assertTrue(exception.getMessage().contains("is already parked"));
    }

    @Test
    void testCheckIn_RejectsWhenLotIsFullForVehicleType() {
        parkingService.checkIn("BUS-1", VehicleType.BUS);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            parkingService.checkIn("CAR-2", VehicleType.CAR);
        });

        assertTrue(exception.getMessage().contains("completely full"));
    }

    @Test
    void testCheckOut_FreesSpotAndSetsExitTime() {
        TicketResponseDto ticket = parkingService.checkIn("AP09-CAR", VehicleType.CAR);

        TicketResponseDto checkedOutTicket = parkingService.checkOut(ticket.getTicketNumber());

        assertNotNull(checkedOutTicket.getExitTime());
        assertNotNull(checkedOutTicket.getFee());

        ParkingSpot dbSpot = spotRepository.findAll().stream()
                .filter(s -> s.getFloor().equals(ticket.getFloor()) && s.getSpotNumber().equals(ticket.getSpotNumber()))
                .findFirst().orElseThrow();
        assertTrue(dbSpot.isAvailable());
    }
}