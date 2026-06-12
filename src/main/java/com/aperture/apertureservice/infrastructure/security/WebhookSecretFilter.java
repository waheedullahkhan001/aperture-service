package com.aperture.apertureservice.infrastructure.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

public class WebhookSecretFilter extends OncePerRequestFilter {

    private final String secret;
    private final ObjectMapper mapper;

    public WebhookSecretFilter(String secret, ObjectMapper mapper) {
        this.secret = secret;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // health: open by design
        // streams/auth: MediaMTX's auth callback has no way to send a custom header;
        // the endpoint is safe without the secret because it validates stream tokens internally
        return uri.startsWith("/actuator/health") || uri.equals("/internal/streams/auth");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = Optional.ofNullable(request.getHeader("Authorization")).orElse("");
        String presented = header.startsWith("Bearer ") ? header.substring(7) : "";
        if (MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
        pd.setProperty("code", "UNAUTHENTICATED");
        response.setStatus(401);
        response.setContentType("application/problem+json");
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
