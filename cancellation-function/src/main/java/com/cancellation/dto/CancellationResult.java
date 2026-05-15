package com.cancellation.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationResult {
    private String tripReference;
    private boolean hotelCancelled;
    private boolean flightCancelled;
    private String hotelStatus;
    private String flightStatus;
    private LocalDateTime processedAt;
    private String message;
}
