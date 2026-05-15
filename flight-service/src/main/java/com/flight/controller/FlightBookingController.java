package com.flight.controller;

import com.flight.dto.FlightBookingRequest;
import com.flight.dto.FlightBookingResponse;
import com.flight.service.FlightBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
public class FlightBookingController {

    private final FlightBookingService flightBookingService;

    @PostMapping("/bookings")
    public ResponseEntity<FlightBookingResponse> bookFlight(
            @Valid @RequestBody FlightBookingRequest request) {
        log.info("Received flight booking request: {}", request);
        FlightBookingResponse response = flightBookingService.bookFlight(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);

    }

    @DeleteMapping("/bookings/{bookingReference}")
    public ResponseEntity<FlightBookingResponse> cancelBooking(
            @PathVariable String bookingReference) {
        log.info("Received cancellation request for: {}", bookingReference);
        return ResponseEntity.ok(flightBookingService.cancelBooking(bookingReference));
    }

    @GetMapping("/bookings/{bookingReference}")
    public ResponseEntity<FlightBookingResponse> getBooking(
            @PathVariable String bookingReference) {
        return ResponseEntity.ok(flightBookingService.getBooking(bookingReference));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBusinessException(RuntimeException e) {
        log.error("Business error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}
