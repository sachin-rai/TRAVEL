package com.hotel.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "hotel_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingReference;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private Integer roomsBooked;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    public enum BookingStatus {
        CONFIRMED, CANCELLED, COMPLETED
    }
}
