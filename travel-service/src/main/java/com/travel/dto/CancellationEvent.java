package com.travel.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Event payload published when a trip is cancelled.
 * Consumed by the serverless cancellation-function.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationEvent {
    private String tripReference;
    private String hotelBookingReference;
    private String flightBookingReference;
    private LocalDateTime cancelledAt;
    private String source;
}
