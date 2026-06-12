package com.aperture.apertureservice.infrastructure.security;

import com.aperture.apertureservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ServletFilterLeakTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void permitAllPathsAreNotStranguledByLeakedWebhookFilter() throws Exception {
        // /api/v1/auth/** is permitAll in the web chain; no controller exists yet, so a healthy
        // stack returns 404/405 — a leaked servlet-level WebhookSecretFilter would return 401.
        assertThat(get("/api/v1/auth/does-not-exist").statusCode()).isNotEqualTo(401);
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    void webChainStillGuardsProtectedPathsOnRealServer() throws Exception {
        HttpResponse<String> r = get("/api/v1/me");
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.body()).contains("UNAUTHENTICATED");
    }

    @Test
    void internalChainStillRequiresSecretOnRealServer() throws Exception {
        // auth endpoint is intentionally open (MediaMTX can't send custom headers); validates tokens internally
        assertThat(get("/internal/streams/auth").statusCode()).isNotEqualTo(401);
        // hooks and metrics still require the shared secret
        assertThat(get("/actuator/metrics").statusCode()).isEqualTo(401);
    }
}
