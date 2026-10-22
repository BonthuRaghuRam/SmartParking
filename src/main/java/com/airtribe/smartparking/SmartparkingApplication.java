package com.airtribe.smartparking;

import com.airtribe.smartparking.model.ParkingSpot;
import com.airtribe.smartparking.model.SpotType;
import com.airtribe.smartparking.repository.ParkingSpotRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.retry.annotation.EnableRetry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
public class SmartparkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartparkingApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public CommandLineRunner initDatabase(ParkingSpotRepository spotRepository) {
        return args -> {
            if (spotRepository.count() == 0) {
                List<ParkingSpot> spots = new ArrayList<>();
                // 3 floors, 20 spots per floor (4 Small, 12 Compact, 4 Large)
                for (int floor = 1; floor <= 3; floor++) {
                    for (int spotNum = 1; spotNum <= 20; spotNum++) {
                        SpotType type;
                        if (spotNum <= 4) type = SpotType.SMALL;
                        else if (spotNum <= 16) type = SpotType.COMPACT;
                        else type = SpotType.LARGE;

                        spots.add(ParkingSpot.builder()
                                .floor(floor)
                                .spotNumber(spotNum)
                                .spotType(type)
                                .isAvailable(true)
                                .build());
                    }
                }
                spotRepository.saveAll(spots);
                System.out.println("Database initialized with 60 parking spots across 3 floors.");
            }
        };
    }
}