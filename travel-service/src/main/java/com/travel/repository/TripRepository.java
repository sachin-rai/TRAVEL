package com.travel.repository;

import com.travel.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByTripReference(String tripReference);
    List<Trip> findByCustomerName(String customerName);
    List<Trip> findByStatusAndStartDateBefore(Trip.TripStatus status, LocalDate date);
}
