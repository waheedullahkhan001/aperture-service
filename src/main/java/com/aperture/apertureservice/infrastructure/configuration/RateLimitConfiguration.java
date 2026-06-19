package com.aperture.apertureservice.infrastructure.configuration;

import com.aperture.apertureservice.infrastructure.security.AuthRateLimitInterceptor;
import io.github.bucket4j.BucketConfiguration;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration implements WebMvcConfigurer {

    private final AuthRateLimitInterceptor interceptor;

    public RateLimitConfiguration(RateLimitProperties props, ObjectMapper mapper) {
        Map<String, BucketConfiguration> buckets = Map.of(
                "/api/v1/auth/login",
                AuthRateLimitInterceptor.perMinute(props.loginPerMinute()),

                "/api/v1/auth/register",
                AuthRateLimitInterceptor.perMinute(props.registerPerMinute()),

                "/api/v1/auth/password-reset/request",
                AuthRateLimitInterceptor.perMinute(props.passwordResetRequestPerMinute()),

                "/api/v1/auth/resend-verification",
                AuthRateLimitInterceptor.perMinute(props.resendVerificationPerMinute())
        );
        this.interceptor = new AuthRateLimitInterceptor(buckets, mapper);
    }

    @Bean
    AuthRateLimitInterceptor authRateLimitInterceptor() {
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/password-reset/request",
                        "/api/v1/auth/resend-verification");
    }
}
