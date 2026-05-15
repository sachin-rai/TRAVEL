package com.travel.client;

import com.travel.dto.FlightBookingRequest;
import com.travel.dto.FlightBookingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "flight-service", url = "${services.flight.url}")
public interface FlightServiceClient {

    @PostMapping("/api/v1/flights/bookings")
    FlightBookingResponse bookFlight(@RequestBody FlightBookingRequest request);

    @DeleteMapping("/api/v1/flights/bookings/{bookingReference}")
    FlightBookingResponse cancelBooking(@PathVariable("bookingReference") String bookingReference);

    @GetMapping("/api/v1/flights/bookings/{bookingReference}")
    FlightBookingResponse getBooking(@PathVariable("bookingReference") String bookingReference);
}
