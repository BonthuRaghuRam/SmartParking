package com.airtribe.smartparking.strategy;

import com.airtribe.smartparking.model.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class HourlyFeeStrategy implements FeeCalculationStrategy {

    private final BigDecimal baseHourlyRate;
    private final BigDecimal motorcycleMultiplier;
    private final BigDecimal carMultiplier;
    private final BigDecimal busMultiplier;

    public HourlyFeeStrategy(
            @Value("${parking.rates.base-hourly:20.00}") BigDecimal baseHourlyRate,
            @Value("${parking.rates.multiplier.motorcycle:0.5}") BigDecimal motorcycleMultiplier,
            @Value("${parking.rates.multiplier.car:1.0}") BigDecimal carMultiplier,
            @Value("${parking.rates.multiplier.bus:2.5}") BigDecimal busMultiplier) {
        this.baseHourlyRate = baseHourlyRate;
        this.motorcycleMultiplier = motorcycleMultiplier;
        this.carMultiplier = carMultiplier;
        this.busMultiplier = busMultiplier;
    }

    @Override
    public BigDecimal calculateFee(Ticket ticket, LocalDateTime checkoutTime) {
        if (ticket.getEntryTime().isAfter(checkoutTime)) {
            throw new IllegalArgumentException("Checkout time cannot be before entry time.");
        }

        long secondsParked = Duration.between(ticket.getEntryTime(), checkoutTime).getSeconds();
        long chargeableHours = (long) Math.ceil(secondsParked / 3600.0);
        chargeableHours = Math.max(1, chargeableHours);

        BigDecimal vehicleMultiplier = switch (ticket.getVehicle().getVehicleType()) {
            case MOTORCYCLE -> motorcycleMultiplier;
            case CAR -> carMultiplier;
            case BUS -> busMultiplier;
        };

        return baseHourlyRate
                .multiply(vehicleMultiplier)
                .multiply(new BigDecimal(chargeableHours))
                .setScale(2, RoundingMode.HALF_UP);
    }
}