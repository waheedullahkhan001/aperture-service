package com.aperture.apertureservice.infrastructure.configuration;

import com.aperture.apertureservice.domain.account.spi.EmailSender;
import com.aperture.apertureservice.domain.account.spi.Users;
import com.aperture.apertureservice.domain.emergency.service.AlertConfigService;
import com.aperture.apertureservice.domain.emergency.service.AlertDispatchService;
import com.aperture.apertureservice.domain.emergency.service.ContactsService;
import com.aperture.apertureservice.domain.emergency.spi.AlertConfigurations;
import com.aperture.apertureservice.domain.emergency.spi.AlertDispatchAttempts;
import com.aperture.apertureservice.domain.emergency.spi.EmergencyContacts;
import com.aperture.apertureservice.domain.recording.spi.Recordings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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

    @Bean
    AlertDispatchService alertDispatchService(Recordings recordings, Users users,
                                              EmergencyContacts contacts, AlertConfigurations configs,
                                              AlertDispatchAttempts attempts, EmailSender emails,
                                              Clock clock, AppProperties props) {
        return new AlertDispatchService(recordings, users, contacts, configs, attempts, emails,
                clock, props.publicOrigin());
    }
}
