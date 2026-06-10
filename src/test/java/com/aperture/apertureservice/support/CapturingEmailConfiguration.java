package com.aperture.apertureservice.support;

import com.aperture.apertureservice.domain.account.spi.stubs.CapturingEmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class CapturingEmailConfiguration {

    @Bean
    @Primary
    public CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }
}
