package com.airtribe.smartparking.dto;

import com.airtribe.smartparking.model.VehicleType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TicketResponseDto {
    private String ticketNumber;
    private String licensePlate;
    private VehicleType vehicleType;
    private Integer floor;
    private Integer spotNumber;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private BigDecimal fee;
}