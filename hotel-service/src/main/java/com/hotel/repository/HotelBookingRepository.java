package com.hotel.repository;

import com.hotel.model.HotelBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HotelBookingRepository extends JpaRepository<HotelBooking, Long> {

    Optional<HotelBooking> findByBookingReference(String bookingReference);

    @Query("SELECT COALESCE(SUM(b.roomsBooked), 0) FROM HotelBooking b " +
           "WHERE b.hotelId = :hotelId AND b.status = 'CONFIRMED' " +
           "AND b.checkInDate < :checkOut AND b.checkOutDate > :checkIn")
    Integer countBookedRooms(@Param("hotelId") Long hotelId,
                             @Param("checkIn") LocalDate checkIn,
                             @Param("checkOut") LocalDate checkOut);

    List<HotelBooking> findByCustomerName(String customerName);
}
