package com.flight.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightBookingResponse {
    private String bookingReference;
    private String airline;
    private String flightNumber;
    private String originCity;
    private String destinationCity;
    private String customerName;
    private Integer seatsBooked;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Double totalAmount;
    private String status;
    private String message;
}
