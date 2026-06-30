package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.domain.account.spi.Sessions;
import com.aperture.apertureservice.domain.account.spi.TokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenIssuer tokenIssuer;
    private final Sessions sessions;

    public JwtAuthenticationFilter(TokenIssuer tokenIssuer, Sessions sessions) {
        this.tokenIssuer = tokenIssuer;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && !header.startsWith("Bearer apd_")) {
            tokenIssuer.validate(header.substring(7)).ifPresentOrElse(claims -> {
                if (sessions.byId(claims.sessionId()).isEmpty()) {
                    request.setAttribute("auth.code", "INVALID_TOKEN");
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(claims.userId(), claims.sessionId()), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }, () -> request.setAttribute("auth.code", "INVALID_TOKEN"));
        }
        chain.doFilter(request, response);
    }
}
