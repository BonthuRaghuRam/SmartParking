package com.airtribe.smartparking.repository;

import com.airtribe.smartparking.model.ParkingSpot;
import com.airtribe.smartparking.model.SpotType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    Optional<ParkingSpot> findFirstBySpotTypeAndIsAvailableTrueOrderByFloorAscSpotNumberAsc(SpotType spotType);

    @Query("SELECT p.floor, p.spotType, COUNT(p) FROM ParkingSpot p WHERE p.isAvailable = true GROUP BY p.floor, p.spotType ORDER BY p.floor ASC")
    List<Object[]> countAvailableSpotsByFloorAndType();

    @Query("SELECT DISTINCT p.floor FROM ParkingSpot p ORDER BY p.floor ASC")
    List<Integer> findAllDistinctFloors();
}