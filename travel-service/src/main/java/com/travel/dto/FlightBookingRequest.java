package com.travel.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightBookingRequest {
    private String customerName;
    private String originCity;
    private String destinationCity;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Integer numberOfPassengers;
}
