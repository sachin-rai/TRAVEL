package com.cancellation.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Lightweight clients used by the serverless function to invoke
 * cancellation APIs of the hotel and flight services.
 *
 * Using plain RestTemplate (not Feign) here because serverless functions
 * benefit from minimal cold-start overhead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingServiceClients {

    private final RestTemplate restTemplate;

    @Value("${services.hotel.url}")
    private String hotelServiceUrl;

    @Value("${services.flight.url}")
    private String flightServiceUrl;

    public boolean cancelHotelBooking(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            log.info("No hotel booking reference to cancel; skipping");
            return true;
        }
        try {
            String url = hotelServiceUrl + "/api/v1/hotels/bookings/" + bookingRef;
            log.info("Calling DELETE {}", url);
            restTemplate.delete(url);
            log.info("Hotel booking {} cancelled successfully", bookingRef);
            return true;
        } catch (Exception e) {
            log.error("Failed to cancel hotel booking {}: {}", bookingRef, e.getMessage());
            return false;
        }
    }

    public boolean cancelFlightBooking(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            log.info("No flight booking reference to cancel; skipping");
            return true;
        }
        try {
            String url = flightServiceUrl + "/api/v1/flights/bookings/" + bookingRef;
            log.info("Calling DELETE {}", url);
            restTemplate.delete(url);
            log.info("Flight booking {} cancelled successfully", bookingRef);
            return true;
        } catch (Exception e) {
            log.error("Failed to cancel flight booking {}: {}", bookingRef, e.getMessage());
            return false;
        }
    }
}
