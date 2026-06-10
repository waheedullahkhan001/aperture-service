package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.ddd.Unauthorized;
import com.aperture.apertureservice.domain.account.DeviceIdentity;
import com.aperture.apertureservice.domain.account.api.IdentifyDevice;
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

public class DeviceTokenAuthenticationFilter extends OncePerRequestFilter {

    private final IdentifyDevice identifyDevice;

    public DeviceTokenAuthenticationFilter(IdentifyDevice identifyDevice) {
        this.identifyDevice = identifyDevice;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer apd_")) {
            try {
                DeviceIdentity id = identifyDevice.identify(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedDevice(id.userId(), id.deviceId(), id.deviceName(), id.userFullname()),
                        null, List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Unauthorized e) {
                request.setAttribute("auth.code", e.code());
            }
        }
        chain.doFilter(request, response);
    }
}
