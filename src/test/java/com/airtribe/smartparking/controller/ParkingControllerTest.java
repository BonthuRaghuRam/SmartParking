package com.airtribe.smartparking.controller;

import com.airtribe.smartparking.dto.CheckInRequest;
import com.airtribe.smartparking.dto.TicketResponseDto;
import com.airtribe.smartparking.exception.TicketNotFoundException;
import com.airtribe.smartparking.model.VehicleType;
import com.airtribe.smartparking.service.ParkingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ParkingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ParkingService parkingService;

    @Test
    void checkIn_success_returns201WithTicket() throws Exception {
        CheckInRequest request = new CheckInRequest();
        request.setLicensePlate("ap09 car");   // setter normalises to AP09CAR
        request.setVehicleType(VehicleType.CAR);

        TicketResponseDto dto = TicketResponseDto.builder()
                .ticketNumber("TKT-ABCD1234")
                .licensePlate("AP09CAR")
                .vehicleType(VehicleType.CAR)
                .floor(1)
                .spotNumber(5)
                .build();
        when(parkingService.checkIn(eq("AP09CAR"), eq(VehicleType.CAR))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket.ticketNumber").value("TKT-ABCD1234"))
                .andExpect(jsonPath("$.ticket.floor").value(1))
                .andExpect(jsonPath("$.ticket.spotNumber").value(5));
    }

    @Test
    void checkIn_missingPlate_returns400() throws Exception {
        CheckInRequest request = new CheckInRequest();
        request.setVehicleType(VehicleType.CAR);

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkIn_invalidVehicleType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\":\"AP09CAR\",\"vehicleType\":\"SPACESHIP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkIn_alreadyParked_returns409() throws Exception {
        CheckInRequest request = new CheckInRequest();
        request.setLicensePlate("AP09-CAR");
        request.setVehicleType(VehicleType.CAR);

        when(parkingService.checkIn(any(), eq(VehicleType.CAR)))
                .thenThrow(new IllegalStateException("Vehicle is already parked."));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void checkOut_success_returns200WithFee() throws Exception {
        TicketResponseDto dto = TicketResponseDto.builder()
                .ticketNumber("TKT-ABCD1234")
                .fee(new BigDecimal("20.00"))
                .build();
        when(parkingService.checkOut("TKT-ABCD1234")).thenReturn(dto);

        mockMvc.perform(post("/api/v1/parking/check-out/TKT-ABCD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.fee").value(20.00));
    }

    @Test
    void checkOut_unknownTicket_returns404() throws Exception {
        when(parkingService.checkOut("TKT-UNKNOWN"))
                .thenThrow(new TicketNotFoundException("Ticket not found"));

        mockMvc.perform(post("/api/v1/parking/check-out/TKT-UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void availability_returns200WithCounts() throws Exception {
        Map<String, Long> floor1 = new LinkedHashMap<>();
        floor1.put("SMALL", 4L);
        floor1.put("COMPACT", 12L);
        floor1.put("LARGE", 4L);
        Map<String, Object> availability = new LinkedHashMap<>();
        availability.put("Floor 1", floor1);
        when(parkingService.getAvailability()).thenReturn(availability);

        mockMvc.perform(get("/api/v1/parking/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Floor 1'].COMPACT").value(12));
    }
}