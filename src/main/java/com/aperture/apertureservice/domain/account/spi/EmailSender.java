package com.aperture.apertureservice.domain.account.spi;

public interface EmailSender {
    void send(String to, String subject, String body);
}
