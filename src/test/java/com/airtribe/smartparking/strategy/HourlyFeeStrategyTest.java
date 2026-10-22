package com.airtribe.smartparking.strategy;

import com.airtribe.smartparking.model.Ticket;
import com.airtribe.smartparking.model.Vehicle;
import com.airtribe.smartparking.model.VehicleType;
import com.airtribe.smartparking.strategy.HourlyFeeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HourlyFeeStrategyTest {

    private HourlyFeeStrategy feeStrategy;
    private final LocalDateTime entry = LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        feeStrategy = new HourlyFeeStrategy(
                new BigDecimal("20.00"),
                new BigDecimal("0.5"),
                new BigDecimal("1.0"),
                new BigDecimal("2.5")
        );
    }

    @Test
    void calculateFee_MotorcycleOneHour_returns10() {
        Ticket t = buildTicket(VehicleType.MOTORCYCLE);
        BigDecimal fee = feeStrategy.calculateFee(t, entry.plusMinutes(60));
        assertEquals(new BigDecimal("10.00"), fee);
    }

    @Test
    void calculateFee_BusPartialHour_roundsUpToMinimumOneHour() {
        Ticket t = buildTicket(VehicleType.BUS);
        BigDecimal fee = feeStrategy.calculateFee(t, entry.plusMinutes(15));
        assertEquals(new BigDecimal("50.00"), fee);
    }

    @Test
    void calculateFee_CarSixtyMinutesOneSecond_roundsUpToTwoHours() {
        Ticket t = buildTicket(VehicleType.CAR);
        BigDecimal fee = feeStrategy.calculateFee(t, entry.plusMinutes(60).plusSeconds(1));
        assertEquals(new BigDecimal("40.00"), fee);
    }

    @Test
    void calculateFee_exitBeforeEntry_throwsException() {
        Ticket t = buildTicket(VehicleType.CAR);
        assertThrows(IllegalArgumentException.class, () -> {
            feeStrategy.calculateFee(t, entry.minusMinutes(5));
        });
    }

    private Ticket buildTicket(VehicleType type) {
        Vehicle v = new Vehicle();
        v.setVehicleType(type);
        Ticket t = new Ticket();
        t.setVehicle(v);
        t.setEntryTime(entry);
        return t;
    }
}