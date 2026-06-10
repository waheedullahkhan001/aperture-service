package com.aperture.apertureservice.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

public class CorsSetup {

    @Configuration
    @Profile("!prod")
    static class Dev {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOriginPatterns(List.of("*"));
            cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
            cfg.setAllowedHeaders(List.of("*"));
            cfg.setMaxAge(1800L);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", cfg);
            return source;
        }
    }

    @Configuration
    @Profile("prod")
    static class Prod {

        @Bean
        CorsConfigurationSource corsConfigurationSource(AppProperties props) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(List.of(props.publicOrigin()));
            cfg.setAllowedMethods(List.of("GET", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("*"));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/public/**", cfg);
            return source;
        }
    }
}
