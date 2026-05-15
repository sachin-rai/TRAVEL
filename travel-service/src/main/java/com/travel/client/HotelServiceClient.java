package com.travel.client;

import com.travel.dto.HotelBookingRequest;
import com.travel.dto.HotelBookingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hotel-service", url = "${services.hotel.url}")
public interface HotelServiceClient {

    @PostMapping("/api/v1/hotels/bookings")
    HotelBookingResponse bookHotel(@RequestBody HotelBookingRequest request);

    @DeleteMapping("/api/v1/hotels/bookings/{bookingReference}")
    HotelBookingResponse cancelBooking(@PathVariable("bookingReference") String bookingReference);

    @GetMapping("/api/v1/hotels/bookings/{bookingReference}")
    HotelBookingResponse getBooking(@PathVariable("bookingReference") String bookingReference);
}
