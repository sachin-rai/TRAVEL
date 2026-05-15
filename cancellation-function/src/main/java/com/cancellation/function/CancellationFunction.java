package com.cancellation.function;

import com.cancellation.client.BookingServiceClients;
import com.cancellation.dto.CancellationEvent;
import com.cancellation.dto.CancellationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * Spring Cloud Function — cloud-agnostic serverless handler.
 *
 * One single function bean is registered, and Spring Cloud Function exposes it via:
 *   - HTTP (POST /cancelTrip)      -> Knative, Cloud Run, Lambda URL, OpenFaaS
 *   - Stream binding (cancelTrip-in-0) -> Kafka, Pub/Sub, Kinesis, EventBridge
 *
 * The same code runs on AWS Lambda, GCP Cloud Functions, Azure Functions, or any K8s
 * cluster running Knative. The deployment target is selected by the CI/CD pipeline.
 *
 * Triggered automatically when the travel-service publishes a cancellation event,
 * this function calls the Hotel and Flight service APIs to roll back their bookings.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CancellationFunction {

    private final BookingServiceClients clients;

    /**
     * Function bean name "cancelTrip" maps to:
     *   - HTTP endpoint /cancelTrip
     *   - Stream binding cancelTrip-in-0
     *   - AWS Lambda handler: org.springframework.cloud.function.adapter.aws.FunctionInvoker
     *     with SPRING_CLOUD_FUNCTION_DEFINITION=cancelTrip
     *   - GCP Cloud Function via the same adapter pattern
     */
    @Bean
    public Function<CancellationEvent, CancellationResult> cancelTrip() {
        return event -> {
            log.info("=== Serverless cancellation triggered === trip={} hotelRef={} flightRef={}",
                    event.getTripReference(),
                    event.getHotelBookingReference(),
                    event.getFlightBookingReference());

            boolean hotelOk = clients.cancelHotelBooking(event.getHotelBookingReference());
            boolean flightOk = clients.cancelFlightBooking(event.getFlightBookingReference());

            String message;
            if (hotelOk && flightOk) {
                message = "All bookings cancelled successfully for trip "
                        + event.getTripReference();
            } else if (!hotelOk && !flightOk) {
                message = "Both hotel and flight cancellation failed for trip "
                        + event.getTripReference() + " - manual intervention required";
            } else {
                message = "Partial cancellation for trip " + event.getTripReference()
                        + " - hotelCancelled=" + hotelOk + " flightCancelled=" + flightOk;
            }

            CancellationResult result = CancellationResult.builder()
                    .tripReference(event.getTripReference())
                    .hotelCancelled(hotelOk)
                    .flightCancelled(flightOk)
                    .hotelStatus(hotelOk ? "CANCELLED" : "FAILED")
                    .flightStatus(flightOk ? "CANCELLED" : "FAILED")
                    .processedAt(LocalDateTime.now())
                    .message(message)
                    .build();

            log.info("Cancellation processed: {}", result);
            return result;
        };
    }
}
