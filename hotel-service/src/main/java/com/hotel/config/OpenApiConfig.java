package com.hotel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hotelServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hotel Service API")
                        .description("Microservice for hotel search and booking")
                        .version("1.0.0"));
    }
}
