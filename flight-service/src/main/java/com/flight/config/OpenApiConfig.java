package com.flight.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flightServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flight Service API")
                        .description("Microservice for flight search and booking")
                        .version("1.0.0"));
    }
}
