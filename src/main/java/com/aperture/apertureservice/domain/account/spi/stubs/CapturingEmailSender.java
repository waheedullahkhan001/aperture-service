package com.aperture.apertureservice.domain.account.spi.stubs;

import com.aperture.apertureservice.ddd.Stub;
import com.aperture.apertureservice.domain.account.spi.EmailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Stub
public class CapturingEmailSender implements EmailSender {
    public record Sent(String to, String subject, String body) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Sent(to, subject, body));
    }

    public List<Sent> all() {
        return List.copyOf(sent);
    }

    public List<Sent> to(String address) {
        return sent.stream().filter(s -> s.to().equals(address)).toList();
    }

    public void clear() {
        sent.clear();
    }
}
