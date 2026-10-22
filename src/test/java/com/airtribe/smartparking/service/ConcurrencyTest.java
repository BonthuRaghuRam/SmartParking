package com.airtribe.smartparking.service;

import com.airtribe.smartparking.dto.TicketResponseDto;
import com.airtribe.smartparking.model.ParkingSpot;
import com.airtribe.smartparking.model.SpotType;
import com.airtribe.smartparking.model.VehicleType;
import com.airtribe.smartparking.repository.ParkingSpotRepository;
import com.airtribe.smartparking.repository.TicketRepository;
import com.airtribe.smartparking.repository.VehicleRepository;
import com.airtribe.smartparking.service.ParkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ConcurrencyTest {

    @Autowired private ParkingService parkingService;
    @Autowired private ParkingSpotRepository spotRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private VehicleRepository vehicleRepository;

    @BeforeEach
    void setup() {
        ticketRepository.deleteAll();
        spotRepository.deleteAll();
        vehicleRepository.deleteAll();

        for (int i = 1; i <= 5; i++) {
            spotRepository.save(ParkingSpot.builder().floor(1).spotNumber(i).spotType(SpotType.COMPACT).isAvailable(true).build());
        }
    }

    @Test
    public void testConcurrentCheckIn() throws InterruptedException {
        int threads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successfulCheckIns = new AtomicInteger(0);
        Set<Integer> assignedSpotNumbers = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    TicketResponseDto ticket = parkingService.checkIn("TS09-C-" + index, VehicleType.CAR);
                    assignedSpotNumbers.add(ticket.getSpotNumber());
                    successfulCheckIns.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertEquals(5, successfulCheckIns.get(), "Exactly 5 spots should be successfully assigned.");
        assertEquals(5, assignedSpotNumbers.size(), "No spot should be assigned twice.");
    }

    @Test
    public void testSamePlateRace() throws InterruptedException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successfulCheckIns = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    parkingService.checkIn("SAME-PLATE-123", VehicleType.CAR);
                    successfulCheckIns.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(1, successfulCheckIns.get(), "Only one check-in should succeed for the same plate.");
    }

    @Test
    public void testConcurrentCheckOut() throws InterruptedException {
        TicketResponseDto ticket = parkingService.checkIn("CHECKOUT-RACE", VehicleType.CAR);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successfulCheckOuts = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    parkingService.checkOut(ticket.getTicketNumber());
                    successfulCheckOuts.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(5, successfulCheckOuts.get(), "Check-out is idempotent, all should succeed and return the same ticket without crashing or losing updates.");
    }
}