package com.aperture.apertureservice.infrastructure.configuration;

import com.aperture.apertureservice.domain.emergency.service.AlertConfigService;
import com.aperture.apertureservice.domain.emergency.service.ContactsService;
import com.aperture.apertureservice.domain.emergency.spi.AlertConfigurations;
import com.aperture.apertureservice.domain.emergency.spi.EmergencyContacts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmergencyConfiguration {

    @Bean
    ContactsService contactsService(EmergencyContacts contacts) {
        return new ContactsService(contacts);
    }

    @Bean
    AlertConfigService alertConfigService(AlertConfigurations configs) {
        return new AlertConfigService(configs);
    }
}
