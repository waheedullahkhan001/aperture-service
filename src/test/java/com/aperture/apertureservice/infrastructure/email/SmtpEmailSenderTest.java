package com.aperture.apertureservice.infrastructure.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailSenderTest {

    @Test
    void buildsSimpleMailMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "noreply@aperture.example");

        sender.send("to@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage m = captor.getValue();
        assertThat(m.getFrom()).isEqualTo("noreply@aperture.example");
        assertThat(m.getTo()).containsExactly("to@example.com");
        assertThat(m.getSubject()).isEqualTo("Subject");
        assertThat(m.getText()).isEqualTo("Body");
    }
}
