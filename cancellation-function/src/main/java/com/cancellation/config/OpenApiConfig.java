package com.cancellation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cancellationFunctionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cancellation Function API")
                        .description("Serverless function for processing booking cancellations")
                        .version("1.0.0"));
    }
}
