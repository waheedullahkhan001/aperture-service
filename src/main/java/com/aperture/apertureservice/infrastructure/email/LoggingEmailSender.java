package com.aperture.apertureservice.infrastructure.email;

import com.aperture.apertureservice.domain.account.spi.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("EMAIL to={} subject={} body={}", to, subject, body);
    }
}
