package com.travel.service;

import com.travel.client.FlightServiceClient;
import com.travel.client.HotelServiceClient;
import com.travel.dto.*;
import com.travel.exception.BookingException;
import com.travel.model.Trip;
import com.travel.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Orchestrates the end-to-end trip booking flow:
 *  1. Receive trip request from customer
 *  2. Try to book hotel for the date range
 *  3. If hotel booking succeeds -> book flight
 *  4. If hotel booking fails  -> DO NOT book flight, mark trip FAILED
 *  5. If flight booking later fails -> compensate by cancelling the hotel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripBookingService {

    private final TripRepository tripRepository;
    private final HotelServiceClient hotelServiceClient;
    private final FlightServiceClient flightServiceClient;
    private final CancellationEventPublisher cancellationEventPublisher;

    @Transactional
    public TripResponse bookTrip(TripRequest request) {
        log.info("=== Starting trip booking workflow for {} ===", request.getCustomerName());

        validateRequest(request);

        String tripRef = "TRIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Trip trip = Trip.builder()
                .tripReference(tripRef)
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .originCity(request.getOriginCity())
                .destinationCity(request.getDestinationCity())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .numberOfTravelers(request.getNumberOfTravelers())
                .numberOfRooms(request.getNumberOfRooms())
                .status(Trip.TripStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .build();
        tripRepository.save(trip);

        // STEP 1: Try hotel booking
        HotelBookingResponse hotelResponse;
        try {
            log.info("Step 1: Attempting hotel booking for trip={}", tripRef);
            hotelResponse = hotelServiceClient.bookHotel(HotelBookingRequest.builder()
                    .customerName(request.getCustomerName())
                    .city(request.getDestinationCity())
                    .checkInDate(request.getStartDate())
                    .checkOutDate(request.getEndDate())
                    .numberOfRooms(request.getNumberOfRooms())
                    .build());
            log.info("Hotel booking OK: ref={}", hotelResponse.getBookingReference());
        } catch (Exception e) {
            log.warn("Hotel booking failed: {}", e.getMessage());
            trip.setStatus(Trip.TripStatus.FAILED);
            trip.setUpdatedAt(LocalDateTime.now());
            tripRepository.save(trip);
            return toResponse(trip,
                    "Hotel not available for the requested dates. Flight booking was NOT attempted.");
        }

        // hotel succeeded — mark intermediate state, then attempt flight
        trip.setHotelBookingReference(hotelResponse.getBookingReference());
        trip.setStatus(Trip.TripStatus.HOTEL_BOOKED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        // STEP 2: Book flight (only because hotel succeeded)
        FlightBookingResponse flightResponse;
        try {
            log.info("Step 2: Attempting flight booking for trip={}", tripRef);
            flightResponse = flightServiceClient.bookFlight(FlightBookingRequest.builder()
                    .customerName(request.getCustomerName())
                    .originCity(request.getOriginCity())
                    .destinationCity(request.getDestinationCity())
                    .departureDate(request.getStartDate())
                    .returnDate(request.getEndDate())
                    .numberOfPassengers(request.getNumberOfTravelers())
                    .build());
            log.info("Flight booking OK: ref={}", flightResponse.getBookingReference());
        } catch (Exception e) {
            // Compensating action: roll back the hotel booking
            log.error("Flight booking failed after hotel was booked; rolling back hotel. err={}",
                    e.getMessage());
            try {
                hotelServiceClient.cancelBooking(hotelResponse.getBookingReference());
                log.info("Compensating cancellation of hotel {} succeeded",
                        hotelResponse.getBookingReference());
            } catch (Exception rollbackErr) {
                log.error("CRITICAL: hotel rollback failed; manual intervention required: {}",
                        rollbackErr.getMessage());
            }
            trip.setStatus(Trip.TripStatus.FAILED);
            trip.setUpdatedAt(LocalDateTime.now());
            tripRepository.save(trip);
            return toResponse(trip,
                    "Flight not available. Hotel booking was rolled back. Trip failed.");
        }

        // STEP 3: Both succeeded — finalize
        trip.setFlightBookingReference(flightResponse.getBookingReference());
        trip.setTotalAmount(hotelResponse.getTotalAmount() + flightResponse.getTotalAmount());
        trip.setStatus(Trip.TripStatus.CONFIRMED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        log.info("=== Trip CONFIRMED: ref={} total=${} ===", tripRef, trip.getTotalAmount());
        return toResponse(trip, "Trip booked successfully. Hotel + Flight confirmed.");
    }

    /**
     * Cancellation entry point — only allowed BEFORE the trip start date.
     * Publishes a cancellation event that is consumed by the serverless cancellation function,
     * which calls Hotel & Flight service cancellation APIs asynchronously.
     */
    @Transactional
    public TripResponse cancelTrip(String tripReference) {
        log.info("Cancellation requested for trip={}", tripReference);

        Trip trip = tripRepository.findByTripReference(tripReference)
                .orElseThrow(() -> new BookingException("Trip not found: " + tripReference));

        if (trip.getStatus() == Trip.TripStatus.CANCELLED) {
            return toResponse(trip, "Trip was already cancelled");
        }
        if (trip.getStatus() == Trip.TripStatus.COMPLETED) {
            throw new BookingException(
                    "Trip already completed; itinerary issued. Cannot cancel.");
        }
        if (!LocalDate.now().isBefore(trip.getStartDate())) {
            throw new BookingException(
                    "Cannot cancel: trip start date has already arrived. Itinerary has been issued.");
        }

        // Mark cancelled FIRST, then publish event so the serverless function
        // takes care of rolling back the downstream bookings asynchronously.
        trip.setStatus(Trip.TripStatus.CANCELLED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        cancellationEventPublisher.publishCancellation(
                tripReference,
                trip.getHotelBookingReference(),
                trip.getFlightBookingReference());

        log.info("Trip {} marked CANCELLED — cancellation event published to serverless function",
                tripReference);

        return toResponse(trip,
                "Trip cancelled. Hotel and Flight cancellations are being processed asynchronously by the cancellation function.");
    }

    public TripResponse getTrip(String tripReference) {
        Trip trip = tripRepository.findByTripReference(tripReference)
                .orElseThrow(() -> new BookingException("Trip not found: " + tripReference));
        return toResponse(trip, "Trip details retrieved");
    }

    /**
     * Issue an itinerary if the customer did NOT cancel before the start date.
     * In production this would be called by a scheduler/cron each morning.
     */
    @Transactional
    public Itinerary issueItinerary(String tripReference) {
        Trip trip = tripRepository.findByTripReference(tripReference)
                .orElseThrow(() -> new BookingException("Trip not found: " + tripReference));

        if (trip.getStatus() != Trip.TripStatus.CONFIRMED
                && trip.getStatus() != Trip.TripStatus.COMPLETED) {
            throw new BookingException("Itinerary only available for confirmed trips");
        }

        if (LocalDate.now().isBefore(trip.getStartDate())) {
            throw new BookingException(
                    "Itinerary is issued on or after the trip start date. Customer can still cancel until then.");
        }

        trip.setStatus(Trip.TripStatus.COMPLETED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        return Itinerary.builder()
                .tripReference(trip.getTripReference())
                .customerName(trip.getCustomerName())
                .customerEmail(trip.getCustomerEmail())
                .originCity(trip.getOriginCity())
                .destinationCity(trip.getDestinationCity())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .hotelBookingReference(trip.getHotelBookingReference())
                .flightBookingReference(trip.getFlightBookingReference())
                .totalAmount(trip.getTotalAmount())
                .issuedAt(LocalDateTime.now())
                .message("Have a great trip! Your hotel and flight are confirmed.")
                .build();
    }

    private void validateRequest(TripRequest r) {
        if (!r.getEndDate().isAfter(r.getStartDate())) {
            throw new BookingException("End date must be after start date");
        }
        if (r.getOriginCity().equalsIgnoreCase(r.getDestinationCity())) {
            throw new BookingException("Origin and destination must differ");
        }
    }

    private TripResponse toResponse(Trip t, String message) {
        return TripResponse.builder()
                .tripReference(t.getTripReference())
                .customerName(t.getCustomerName())
                .originCity(t.getOriginCity())
                .destinationCity(t.getDestinationCity())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .numberOfTravelers(t.getNumberOfTravelers())
                .numberOfRooms(t.getNumberOfRooms())
                .hotelBookingReference(t.getHotelBookingReference())
                .flightBookingReference(t.getFlightBookingReference())
                .totalAmount(t.getTotalAmount())
                .status(t.getStatus().name())
                .message(message)
                .build();
    }
}
