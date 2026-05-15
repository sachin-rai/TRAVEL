package com.hotel.service;

import com.hotel.dto.HotelBookingRequest;
import com.hotel.dto.HotelBookingResponse;
import com.hotel.model.Hotel;
import com.hotel.model.HotelBooking;
import com.hotel.repository.HotelBookingRepository;
import com.hotel.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelBookingService {

    private final HotelRepository hotelRepository;
    private final HotelBookingRepository bookingRepository;

    @Transactional
    public HotelBookingResponse bookHotel(HotelBookingRequest request) {
        log.info("Processing hotel booking for customer={} city={} rooms={}",
                request.getCustomerName(), request.getCity(), request.getNumberOfRooms());

        if (!request.getCheckOutDate().isAfter(request.getCheckInDate())) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        List<Hotel> hotelsInCity = hotelRepository.findByCityIgnoreCase(request.getCity());
        if (hotelsInCity.isEmpty()) {
            throw new IllegalStateException("No hotels available in city: " + request.getCity());
        }

        // Find first hotel with enough availability for the requested date range
        Hotel chosenHotel = null;
        for (Hotel hotel : hotelsInCity) {
            Integer booked = bookingRepository.countBookedRooms(
                    hotel.getId(), request.getCheckInDate(), request.getCheckOutDate());
            int available = hotel.getTotalRooms() - (booked == null ? 0 : booked);
            if (available >= request.getNumberOfRooms()) {
                chosenHotel = hotel;
                break;
            }
        }

        if (chosenHotel == null) {
            throw new IllegalStateException(
                    "No hotel has " + request.getNumberOfRooms()
                            + " rooms available in " + request.getCity()
                            + " for the requested dates");
        }

        long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        double total = nights * request.getNumberOfRooms() * chosenHotel.getPricePerNight();
        String reference = "HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HotelBooking booking = HotelBooking.builder()
                .bookingReference(reference)
                .hotelId(chosenHotel.getId())
                .customerName(request.getCustomerName())
                .city(request.getCity())
                .roomsBooked(request.getNumberOfRooms())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .totalAmount(total)
                .status(HotelBooking.BookingStatus.CONFIRMED)
                .build();

        bookingRepository.save(booking);
        log.info("Hotel booking confirmed: reference={}", reference);

        return HotelBookingResponse.builder()
                .bookingReference(reference)
                .hotelName(chosenHotel.getName())
                .city(chosenHotel.getCity())
                .customerName(request.getCustomerName())
                .roomsBooked(request.getNumberOfRooms())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .totalAmount(total)
                .status(booking.getStatus().name())
                .message("Hotel booking confirmed")
                .build();
    }

    @Transactional
    public HotelBookingResponse cancelBooking(String bookingReference) {
        log.info("Cancelling hotel booking: reference={}", bookingReference);

        HotelBooking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hotel booking not found: " + bookingReference));

        if (booking.getStatus() == HotelBooking.BookingStatus.CANCELLED) {
            return HotelBookingResponse.builder()
                    .bookingReference(bookingReference)
                    .status(booking.getStatus().name())
                    .message("Booking was already cancelled")
                    .build();
        }

        booking.setStatus(HotelBooking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Hotel hotel = hotelRepository.findById(booking.getHotelId()).orElse(null);
        return HotelBookingResponse.builder()
                .bookingReference(bookingReference)
                .hotelName(hotel != null ? hotel.getName() : null)
                .city(booking.getCity())
                .customerName(booking.getCustomerName())
                .roomsBooked(booking.getRoomsBooked())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus().name())
                .message("Hotel booking cancelled successfully")
                .build();
    }

    public HotelBookingResponse getBooking(String bookingReference) {
        HotelBooking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hotel booking not found: " + bookingReference));
        Hotel hotel = hotelRepository.findById(booking.getHotelId()).orElse(null);

        return HotelBookingResponse.builder()
                .bookingReference(booking.getBookingReference())
                .hotelName(hotel != null ? hotel.getName() : null)
                .city(booking.getCity())
                .customerName(booking.getCustomerName())
                .roomsBooked(booking.getRoomsBooked())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus().name())
                .message("Booking details retrieved")
                .build();
    }
}
