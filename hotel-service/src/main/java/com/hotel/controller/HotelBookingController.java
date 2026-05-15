package com.hotel.controller;

import com.hotel.dto.HotelBookingRequest;
import com.hotel.dto.HotelBookingResponse;
import com.hotel.service.HotelBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
public class HotelBookingController {

    private final HotelBookingService hotelBookingService;

    @PostMapping("/bookings")
    public ResponseEntity<HotelBookingResponse> bookHotel(
            @Valid @RequestBody HotelBookingRequest request) {
        log.info("Received hotel booking request: {}", request);
        HotelBookingResponse response = hotelBookingService.bookHotel(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @DeleteMapping("/bookings/{bookingReference}")
    public ResponseEntity<HotelBookingResponse> cancelBooking(
            @PathVariable String bookingReference) {
        log.info("Received cancellation request for: {}", bookingReference);
        return ResponseEntity.ok(hotelBookingService.cancelBooking(bookingReference));
    }

    @GetMapping("/bookings/{bookingReference}")
    public ResponseEntity<HotelBookingResponse> getBooking(
            @PathVariable String bookingReference) {
        return ResponseEntity.ok(hotelBookingService.getBooking(bookingReference));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBusinessException(RuntimeException e) {
        log.error("Business error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}
