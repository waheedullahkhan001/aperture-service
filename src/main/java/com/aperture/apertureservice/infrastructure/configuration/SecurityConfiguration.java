package com.aperture.apertureservice.infrastructure.configuration;

import com.aperture.apertureservice.domain.account.api.IdentifyDevice;
import com.aperture.apertureservice.domain.account.spi.Sessions;
import com.aperture.apertureservice.domain.account.spi.TokenIssuer;
import com.aperture.apertureservice.infrastructure.security.DeviceTokenAuthenticationFilter;
import com.aperture.apertureservice.infrastructure.security.JwtAuthenticationFilter;
import com.aperture.apertureservice.infrastructure.security.ProblemAuthEntryPoint;
import com.aperture.apertureservice.infrastructure.security.WebhookSecretFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    ProblemAuthEntryPoint problemAuthEntryPoint(ObjectMapper mapper) {
        return new ProblemAuthEntryPoint(mapper);
    }

    @Bean
    WebhookSecretFilter webhookSecretFilter(AppProperties props, ObjectMapper mapper) {
        return new WebhookSecretFilter(props.webhookSecret(), mapper);
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(TokenIssuer tokenIssuer, Sessions sessions) {
        return new JwtAuthenticationFilter(tokenIssuer, sessions);
    }

    @Bean
    DeviceTokenAuthenticationFilter deviceTokenAuthenticationFilter(IdentifyDevice identifyDevice) {
        return new DeviceTokenAuthenticationFilter(identifyDevice);
    }

    // The three filters below are security-chain members only. Boot auto-registers raw Filter
    // beans into the global servlet chain as well — these disabled registrations prevent that
    // (otherwise WebhookSecretFilter would 401 every non-actuator request on a real server).
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter f) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    FilterRegistrationBean<DeviceTokenAuthenticationFilter> deviceFilterRegistration(DeviceTokenAuthenticationFilter f) {
        FilterRegistrationBean<DeviceTokenAuthenticationFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    FilterRegistrationBean<WebhookSecretFilter> webhookFilterRegistration(WebhookSecretFilter f) {
        FilterRegistrationBean<WebhookSecretFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalChain(HttpSecurity http, WebhookSecretFilter webhookFilter) throws Exception {
        http.securityMatcher("/internal/**", "/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(webhookFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain deviceChain(HttpSecurity http, DeviceTokenAuthenticationFilter deviceFilter,
                                    ProblemAuthEntryPoint entryPoint) throws Exception {
        http.securityMatcher("/api/v1/device/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(deviceFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("DEVICE"));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain webChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                 ProblemAuthEntryPoint entryPoint) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/logout").hasRole("USER")
                        .requestMatchers("/api/v1/auth/**", "/api/public/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/error").permitAll()
                        .anyRequest().hasRole("USER"));
        return http.build();
    }
}
