package com.travel.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Itinerary {
    private String tripReference;
    private String customerName;
    private String customerEmail;
    private String originCity;
    private String destinationCity;
    private LocalDate startDate;
    private LocalDate endDate;
    private String hotelBookingReference;
    private String flightBookingReference;
    private Double totalAmount;
    private LocalDateTime issuedAt;
    private String message;
}
