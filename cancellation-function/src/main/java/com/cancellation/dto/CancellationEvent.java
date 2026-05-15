package com.cancellation.dto;

import lombok.*;
import java.time.LocalDateTime;

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
