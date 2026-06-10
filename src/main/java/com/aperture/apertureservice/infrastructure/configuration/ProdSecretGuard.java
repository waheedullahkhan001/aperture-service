package com.aperture.apertureservice.infrastructure.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdSecretGuard {

    static final String DEV_JWT_SENTINEL =
            "ZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZA==";

    private final AppProperties props;

    public ProdSecretGuard(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void check() {
        if (DEV_JWT_SENTINEL.equals(props.jwt().secret())) {
            throw new IllegalStateException("JWT_SECRET must be set to a real secret in prod");
        }
        if (props.webhookSecret() == null || props.webhookSecret().startsWith("dev-")) {
            throw new IllegalStateException("MEDIAMTX_WEBHOOK_SECRET must be set to a real secret in prod");
        }
    }
}
