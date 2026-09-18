package com.damonskappel.flighttracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/flights/**").allowedOrigins("http://localhost:8080", "http://127.0.0.1:8080", "null").allowedMethods("GET").allowedHeaders("*").maxAge(3600).allowedOrigins(
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "null",
                "https://flighttracker.damonskappel.com"
        );

        registry.addMapping("/stats").allowedOrigins("http://localhost:8080", "http:127.0.0.1:8080", "null").allowedMethods("GET").allowedHeaders("*").maxAge(3600).allowedOrigins(
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "null",
                "https://flighttracker.damonskappel.com"
        );


    }
}
