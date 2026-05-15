package com.travel.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripResponse {
    private String tripReference;
    private String customerName;
    private String originCity;
    private String destinationCity;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer numberOfTravelers;
    private Integer numberOfRooms;
    private String hotelBookingReference;
    private String flightBookingReference;
    private Double totalAmount;
    private String status;
    private String message;
}
