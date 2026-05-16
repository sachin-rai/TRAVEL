package com.travel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI travelServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Travel Orchestrator Service API")
                        .description("Microservice for orchestrating flight and hotel bookings")
                        .version("1.0.0"));
    }
}
