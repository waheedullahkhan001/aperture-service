package com.aperture.apertureservice;

import org.springframework.boot.SpringApplication;

public class TestApertureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(ApertureServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
