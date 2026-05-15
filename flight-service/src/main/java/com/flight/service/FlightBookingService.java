package com.flight.service;

import com.flight.dto.FlightBookingRequest;
import com.flight.dto.FlightBookingResponse;
import com.flight.model.Flight;
import com.flight.model.FlightBooking;
import com.flight.repository.FlightBookingRepository;
import com.flight.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightBookingService {

    private final FlightRepository flightRepository;
    private final FlightBookingRepository bookingRepository;

    @Transactional
    public FlightBookingResponse bookFlight(FlightBookingRequest request) {
        log.info("Processing flight booking for customer={} from={} to={} pax={}",
                request.getCustomerName(), request.getOriginCity(),
                request.getDestinationCity(), request.getNumberOfPassengers());

        if (!request.getReturnDate().isAfter(request.getDepartureDate())) {
            throw new IllegalArgumentException("Return date must be after departure date");
        }

        List<Flight> flights = flightRepository
                .findByOriginCityIgnoreCaseAndDestinationCityIgnoreCase(
                        request.getOriginCity(), request.getDestinationCity());

        if (flights.isEmpty()) {
            throw new IllegalStateException("No flights available between "
                    + request.getOriginCity() + " and " + request.getDestinationCity());
        }

        Flight chosen = null;
        for (Flight f : flights) {
            Integer booked = bookingRepository.countBookedSeatsOnDate(
                    f.getId(), request.getDepartureDate());
            int available = f.getTotalSeats() - (booked == null ? 0 : booked);
            if (available >= request.getNumberOfPassengers()) {
                chosen = f;
                break;
            }
        }

        if (chosen == null) {
            throw new IllegalStateException("No flight has "
                    + request.getNumberOfPassengers() + " seats available for the requested dates");
        }

        double total = chosen.getPricePerSeat() * request.getNumberOfPassengers() * 2; // round trip
        String reference = "FB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        FlightBooking booking = FlightBooking.builder()
                .bookingReference(reference)
                .flightId(chosen.getId())
                .customerName(request.getCustomerName())
                .originCity(request.getOriginCity())
                .destinationCity(request.getDestinationCity())
                .seatsBooked(request.getNumberOfPassengers())
                .departureDate(request.getDepartureDate())
                .returnDate(request.getReturnDate())
                .totalAmount(total)
                .status(FlightBooking.BookingStatus.CONFIRMED)
                .build();

        bookingRepository.save(booking);
        log.info("Flight booking confirmed: reference={}", reference);

        return FlightBookingResponse.builder()
                .bookingReference(reference)
                .airline(chosen.getAirline())
                .flightNumber(chosen.getFlightNumber())
                .originCity(chosen.getOriginCity())
                .destinationCity(chosen.getDestinationCity())
                .customerName(request.getCustomerName())
                .seatsBooked(request.getNumberOfPassengers())
                .departureDate(request.getDepartureDate())
                .returnDate(request.getReturnDate())
                .totalAmount(total)
                .status(booking.getStatus().name())
                .message("Flight booking confirmed (round trip)")
                .build();
    }

    @Transactional
    public FlightBookingResponse cancelBooking(String bookingReference) {
        log.info("Cancelling flight booking: reference={}", bookingReference);

        FlightBooking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Flight booking not found: " + bookingReference));

        if (booking.getStatus() == FlightBooking.BookingStatus.CANCELLED) {
            return FlightBookingResponse.builder()
                    .bookingReference(bookingReference)
                    .status(booking.getStatus().name())
                    .message("Booking was already cancelled")
                    .build();
        }

        booking.setStatus(FlightBooking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Flight flight = flightRepository.findById(booking.getFlightId()).orElse(null);
        return FlightBookingResponse.builder()
                .bookingReference(bookingReference)
                .airline(flight != null ? flight.getAirline() : null)
                .flightNumber(flight != null ? flight.getFlightNumber() : null)
                .originCity(booking.getOriginCity())
                .destinationCity(booking.getDestinationCity())
                .customerName(booking.getCustomerName())
                .seatsBooked(booking.getSeatsBooked())
                .departureDate(booking.getDepartureDate())
                .returnDate(booking.getReturnDate())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus().name())
                .message("Flight booking cancelled successfully")
                .build();
    }

    public FlightBookingResponse getBooking(String bookingReference) {
        FlightBooking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Flight booking not found: " + bookingReference));
        Flight flight = flightRepository.findById(booking.getFlightId()).orElse(null);

        return FlightBookingResponse.builder()
                .bookingReference(booking.getBookingReference())
                .airline(flight != null ? flight.getAirline() : null)
                .flightNumber(flight != null ? flight.getFlightNumber() : null)
                .originCity(booking.getOriginCity())
                .destinationCity(booking.getDestinationCity())
                .customerName(booking.getCustomerName())
                .seatsBooked(booking.getSeatsBooked())
                .departureDate(booking.getDepartureDate())
                .returnDate(booking.getReturnDate())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus().name())
                .message("Booking details retrieved")
                .build();
    }
}
