package com.airtribe.smartparking.strategy;

import com.airtribe.smartparking.model.Ticket;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface FeeCalculationStrategy {
    BigDecimal calculateFee(Ticket ticket, LocalDateTime checkoutTime);
}