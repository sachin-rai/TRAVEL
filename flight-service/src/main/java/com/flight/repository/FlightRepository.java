package com.flight.repository;

import com.flight.model.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {
    List<Flight> findByOriginCityIgnoreCaseAndDestinationCityIgnoreCase(
            String originCity, String destinationCity);
}
