package com.aperture.apertureservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApertureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApertureServiceApplication.class, args);
    }

}
