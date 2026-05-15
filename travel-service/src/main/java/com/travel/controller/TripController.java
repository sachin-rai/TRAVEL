package com.travel.controller;

import com.travel.dto.Itinerary;
import com.travel.dto.TripRequest;
import com.travel.dto.TripResponse;
import com.travel.exception.BookingException;
import com.travel.service.TripBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripBookingService tripBookingService;

    /**
     * Customer requests a new trip booking.
     * Travel service orchestrates hotel -> flight bookings.
     */
    @PostMapping
    public ResponseEntity<TripResponse> bookTrip(@Valid @RequestBody TripRequest request) {
        log.info("Received trip booking request: {}", request);
        TripResponse response = tripBookingService.bookTrip(request);
        HttpStatus status = "FAILED".equals(response.getStatus())
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.CREATED;
        return new ResponseEntity<>(response, status);
    }

    /**
     * Customer cancels the trip before start date.
     * Triggers the serverless cancellation function.
     */
    @DeleteMapping("/{tripReference}")
    public ResponseEntity<TripResponse> cancelTrip(@PathVariable String tripReference) {
        log.info("Received cancellation for trip={}", tripReference);
        return ResponseEntity.ok(tripBookingService.cancelTrip(tripReference));
    }

    @GetMapping("/{tripReference}")
    public ResponseEntity<TripResponse> getTrip(@PathVariable String tripReference) {
        return ResponseEntity.ok(tripBookingService.getTrip(tripReference));
    }

    /**
     * Issue the itinerary for a trip. In production, called by a scheduler
     * on the morning of the trip start date.
     */
    @PostMapping("/{tripReference}/itinerary")
    public ResponseEntity<Itinerary> issueItinerary(@PathVariable String tripReference) {
        return ResponseEntity.ok(tripBookingService.issueItinerary(tripReference));
    }

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<Map<String, String>> handleBookingException(BookingException e) {
        log.error("Booking error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}
