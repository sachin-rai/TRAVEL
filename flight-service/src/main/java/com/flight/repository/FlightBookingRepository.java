package com.flight.repository;

import com.flight.model.FlightBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface FlightBookingRepository extends JpaRepository<FlightBooking, Long> {

    Optional<FlightBooking> findByBookingReference(String bookingReference);

    @Query("SELECT COALESCE(SUM(b.seatsBooked), 0) FROM FlightBooking b " +
           "WHERE b.flightId = :flightId AND b.status = 'CONFIRMED' " +
           "AND b.departureDate <= :date AND b.returnDate >= :date")
    Integer countBookedSeatsOnDate(@Param("flightId") Long flightId,
                                   @Param("date") LocalDate date);
}
