package com.airtribe.smartparking.model;

import com.airtribe.smartparking.model.SpotType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parking_spots", indexes = {
        @Index(name = "idx_spot_type_available", columnList = "spot_type, is_available")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer floor;

    @Column(name = "spot_number", nullable = false)
    private Integer spotNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "spot_type", nullable = false)
    private SpotType spotType;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @Version
    private Long version;
}