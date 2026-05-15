package com.hotel.config;

import com.hotel.model.Hotel;
import com.hotel.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotelDataLoader implements CommandLineRunner {

    private final HotelRepository hotelRepository;

    @Override
    public void run(String... args) {
        if (hotelRepository.count() > 0) {
            return;
        }
        log.info("Seeding sample hotel data...");
        hotelRepository.saveAll(List.of(
            Hotel.builder().name("Grand Plaza Paris").city("Paris")
                    .totalRooms(50).pricePerNight(180.0).build(),
            Hotel.builder().name("Le Royal").city("Paris")
                    .totalRooms(30).pricePerNight(220.0).build(),
            Hotel.builder().name("Tokyo Bay Hotel").city("Tokyo")
                    .totalRooms(80).pricePerNight(150.0).build(),
            Hotel.builder().name("Shibuya Sky Inn").city("Tokyo")
                    .totalRooms(40).pricePerNight(130.0).build(),
            Hotel.builder().name("Manhattan Suites").city("New York")
                    .totalRooms(60).pricePerNight(250.0).build(),
            Hotel.builder().name("Times Square Hotel").city("New York")
                    .totalRooms(45).pricePerNight(290.0).build(),
            Hotel.builder().name("Bali Beach Resort").city("Bali")
                    .totalRooms(35).pricePerNight(120.0).build(),
            Hotel.builder().name("London Tower Hotel").city("London")
                    .totalRooms(55).pricePerNight(200.0).build()
        ));
        log.info("Loaded {} hotels", hotelRepository.count());
    }
}
