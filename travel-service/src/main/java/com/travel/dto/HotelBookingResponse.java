package com.travel.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelBookingResponse {
    private String bookingReference;
    private String hotelName;
    private String city;
    private String customerName;
    private Integer roomsBooked;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Double totalAmount;
    private String status;
    private String message;
}
