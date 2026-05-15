package com.flight.config;

import com.flight.model.Flight;
import com.flight.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightDataLoader implements CommandLineRunner {

    private final FlightRepository flightRepository;

    @Override
    public void run(String... args) {
        if (flightRepository.count() > 0) return;
        log.info("Seeding sample flight data...");
        flightRepository.saveAll(List.of(
            Flight.builder().flightNumber("AF101").airline("Air France")
                    .originCity("New York").destinationCity("Paris")
                    .totalSeats(200).pricePerSeat(450.0).build(),
            Flight.builder().flightNumber("AF202").airline("Air France")
                    .originCity("London").destinationCity("Paris")
                    .totalSeats(180).pricePerSeat(180.0).build(),
            Flight.builder().flightNumber("JL301").airline("Japan Airlines")
                    .originCity("New York").destinationCity("Tokyo")
                    .totalSeats(250).pricePerSeat(900.0).build(),
            Flight.builder().flightNumber("JL302").airline("Japan Airlines")
                    .originCity("London").destinationCity("Tokyo")
                    .totalSeats(220).pricePerSeat(750.0).build(),
            Flight.builder().flightNumber("DL401").airline("Delta")
                    .originCity("Chicago").destinationCity("New York")
                    .totalSeats(180).pricePerSeat(220.0).build(),
            Flight.builder().flightNumber("DL402").airline("Delta")
                    .originCity("Chicago").destinationCity("Paris")
                    .totalSeats(200).pricePerSeat(480.0).build(),
            Flight.builder().flightNumber("BA501").airline("British Airways")
                    .originCity("New York").destinationCity("London")
                    .totalSeats(220).pricePerSeat(420.0).build(),
            Flight.builder().flightNumber("GA601").airline("Garuda")
                    .originCity("Chicago").destinationCity("Bali")
                    .totalSeats(150).pricePerSeat(1100.0).build()
        ));
        log.info("Loaded {} flights", flightRepository.count());
    }
}
