package com.travel.service;

import com.travel.model.Trip;
import com.travel.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs each morning. For every CONFIRMED trip where today == startDate (and
 * the user did NOT cancel before that), issues the itinerary by marking the
 * trip COMPLETED. In production, the itinerary would be emailed here.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ItineraryScheduler {

    private final TripRepository tripRepository;
    private final TripBookingService tripBookingService;

    @Scheduled(cron = "${itinerary.scheduler.cron:0 0 6 * * *}")
    public void issueItinerariesForToday() {
        log.info("Itinerary scheduler running");
        LocalDate today = LocalDate.now();
        List<Trip> tripsStartingToday =
                tripRepository.findByStatusAndStartDateBefore(
                        Trip.TripStatus.CONFIRMED, today.plusDays(1));
        for (Trip trip : tripsStartingToday) {
            if (trip.getStartDate().isEqual(today)) {
                try {
                    tripBookingService.issueItinerary(trip.getTripReference());
                    log.info("Itinerary issued for trip={}", trip.getTripReference());
                } catch (Exception e) {
                    log.error("Failed to issue itinerary for trip={}: {}",
                            trip.getTripReference(), e.getMessage());
                }
            }
        }
    }
}
