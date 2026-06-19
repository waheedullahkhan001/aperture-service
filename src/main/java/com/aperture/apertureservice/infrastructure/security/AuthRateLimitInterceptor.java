package com.aperture.apertureservice.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Per-client-IP token-bucket rate limiter for the abuse-prone unauthenticated auth endpoints.
 *
 * <p>Client IP is resolved from the LAST (rightmost) value in {@code X-Forwarded-For} — the
 * real peer that the single trusted proxy (nginx) appends; leftmost values are client-spoofable.
 * Falls back to {@code request.getRemoteAddr()} for direct/dev/test traffic.
 *
 * <p>Implemented as a Spring MVC {@link HandlerInterceptor} (registered only for the target
 * paths) rather than a servlet {@code Filter} to avoid the Boot 4 servlet-filter auto-
 * registration leak described in {@code ServletFilterLeakTest}.
 *
 * <p>Buckets are stored in a size-bounded Caffeine cache (max 100 000 IPs, evict after 10 min
 * idle) so memory growth is bounded even under adversarial traffic.
 */
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    /** Maps the exact URI suffix (after /api/v1/auth) to its per-minute bucket capacity. */
    private final Map<String, BucketConfiguration> pathBuckets;
    private final Cache<String, Bucket> bucketCache;
    private final ObjectMapper mapper;

    public AuthRateLimitInterceptor(
            Map<String, BucketConfiguration> pathBuckets,
            ObjectMapper mapper) {
        this.pathBuckets = pathBuckets;
        this.mapper = mapper;
        this.bucketCache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }

    /** Build a {@link BucketConfiguration} refilling {@code tokensPerMinute} per minute. */
    public static BucketConfiguration perMinute(long tokensPerMinute) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tokensPerMinute)
                        .refillGreedy(tokensPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        String path = request.getRequestURI();
        BucketConfiguration cfg = pathBuckets.get(path);
        if (cfg == null) {
            return true; // path not rate-limited
        }

        String ip = resolveClientIp(request);
        String cacheKey = path + "|" + ip;

        Bucket bucket = bucketCache.get(cacheKey, k -> Bucket.builder()
                .addLimit(cfg.getBandwidths()[0])
                .build());

        if (bucket.tryConsume(1)) {
            return true;
        }

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Too many requests — please slow down");
        pd.setProperty("code", "RATE_LIMITED");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/problem+json");
        mapper.writeValue(response.getOutputStream(), pd);
        return false;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the LAST (rightmost) entry. The backend is loopback-only behind exactly one
            // trusted proxy (nginx), which APPENDS the real peer to whatever the client sent
            // ("$proxy_add_x_forwarded_for"). The leftmost values are client-supplied and
            // spoofable — using them would let an attacker rotate a fake X-Forwarded-For per
            // request and bypass the limit entirely. The rightmost entry is nginx's observed
            // client and cannot be forged through the proxy.
            int comma = xff.lastIndexOf(',');
            return (comma == -1 ? xff : xff.substring(comma + 1)).strip();
        }
        return request.getRemoteAddr();
    }
}
