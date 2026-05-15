package com.travel.service;

import com.travel.dto.CancellationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * Publishes cancellation events to the serverless cancellation function.
 *
 * Cloud-agnostic dispatch — the trigger mode is selected via property
 * `function.dispatch.mode`:
 *  - "stream"  -> via Spring Cloud Stream binder (Kafka / Kinesis / Pub/Sub / EventHub)
 *  - "http"    -> direct HTTP POST to function URL (Knative / Cloud Run / Lambda URL / OpenFaaS)
 *
 * The serverless function (cancellation-function module) listens on both
 * channels, so the deployment pipeline picks whichever fits the target cloud.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationEventPublisher {

    private final StreamBridge streamBridge;
    private final RestTemplate restTemplate;

    @Value("${function.dispatch.mode:stream}")
    private String dispatchMode;

    @Value("${function.cancellation.url:}")
    private String cancellationFunctionUrl;

    @Value("${function.cancellation.binding:cancellationEvents-out-0}")
    private String streamBinding;

    @Async
    public void publishCancellation(String tripReference,
                                    String hotelBookingReference,
                                    String flightBookingReference) {
        CancellationEvent event = CancellationEvent.builder()
                .tripReference(tripReference)
                .hotelBookingReference(hotelBookingReference)
                .flightBookingReference(flightBookingReference)
                .cancelledAt(LocalDateTime.now())
                .source("travel-service")
                .build();

        if ("http".equalsIgnoreCase(dispatchMode)) {
            publishOverHttp(event);
        } else {
            publishOverStream(event);
        }
    }

    private void publishOverStream(CancellationEvent event) {
        try {
            log.info("Publishing cancellation event over STREAM binding={} event={}",
                    streamBinding, event);
            streamBridge.send(streamBinding,
                    MessageBuilder.withPayload(event)
                            .setHeader("contentType", MediaType.APPLICATION_JSON_VALUE)
                            .build());
            log.info("Cancellation event published successfully (stream)");
        } catch (Exception e) {
            log.error("Failed to publish cancellation event over stream: {}", e.getMessage());
            // Fallback to HTTP if a function URL is configured
            if (cancellationFunctionUrl != null && !cancellationFunctionUrl.isBlank()) {
                log.warn("Falling back to HTTP trigger");
                publishOverHttp(event);
            }
        }
    }

    private void publishOverHttp(CancellationEvent event) {
        try {
            log.info("Publishing cancellation event over HTTP url={} event={}",
                    cancellationFunctionUrl, event);
            restTemplate.postForEntity(cancellationFunctionUrl, event, String.class);
            log.info("Cancellation event published successfully (http)");
        } catch (Exception e) {
            log.error("Failed to publish cancellation event over HTTP: {}", e.getMessage());
        }
    }
}
