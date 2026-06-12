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

    /**
     * MediaMTX's authHTTPAddress callback cannot send custom headers, but it preserves the
     * configured URL's query string verbatim (verified against v1.19.1) — so this one path
     * may present the shared secret as ?secret=... instead of the Authorization header.
     * Query strings can leak into access logs, which is why the fallback is not global.
     */
    private static final String MEDIAMTX_AUTH_CALLBACK = "/internal/streams/auth";

    private final String secret;
    private final ObjectMapper mapper;

    public WebhookSecretFilter(String secret, ObjectMapper mapper) {
        this.secret = secret;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = Optional.ofNullable(request.getHeader("Authorization")).orElse("");
        String presented = header.startsWith("Bearer ") ? header.substring(7) : "";
        if (presented.isEmpty() && MEDIAMTX_AUTH_CALLBACK.equals(request.getRequestURI())) {
            presented = Optional.ofNullable(request.getParameter("secret")).orElse("");
        }
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
