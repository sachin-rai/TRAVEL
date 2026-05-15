package com.travel.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tripReference;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private String originCity;

    @Column(nullable = false)
    private String destinationCity;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer numberOfTravelers;

    @Column(nullable = false)
    private Integer numberOfRooms;

    private String hotelBookingReference;
    private String flightBookingReference;

    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum TripStatus {
        INITIATED,        // request received
        HOTEL_BOOKED,     // hotel succeeded, flight in progress
        CONFIRMED,        // hotel + flight booked
        FAILED,           // hotel unavailable -> flight not attempted
        CANCELLED,        // user cancelled
        COMPLETED         // trip dates passed without cancellation -> itinerary issued
    }
}
