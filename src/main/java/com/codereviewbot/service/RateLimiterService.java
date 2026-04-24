package com.codereviewbot.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-repository rate limiter using the token-bucket algorithm (Bucket4j).
 *
 * HOW IT WORKS:
 *  Each GitHub repository gets its own token bucket.
 *  The bucket starts with N tokens. Each webhook request consumes 1 token.
 *  Tokens refill gradually over the configured window.
 *  When a bucket is empty → request is rate-limited (we return 429).
 *
 * WHY PER-REPO:
 *  A spammer sending fake webhooks for one repo shouldn't block reviews
 *  for another legitimate repo.
 */
@Slf4j
@Service
public class RateLimiterService {

    @Value("${app.rate.limit.requests}")
    private int maxRequests;

    @Value("${app.rate.limit.minutes}")
    private int windowMinutes;

    /**
     * One bucket per repository full_name (e.g. "Aashish2409/my-project").
     * ConcurrentHashMap is used for thread safety under concurrent webhook hits.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Returns true if this repository is allowed to trigger a review right now.
     * Returns false if the rate limit is exceeded.
     *
     * @param repoFullName e.g. "Aashish2409/codereview-bot"
     */
    public boolean tryConsume(String repoFullName) {
        Bucket bucket = buckets.computeIfAbsent(repoFullName, this::createNewBucket);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for repo: {}. Max {} requests per {} min.",
                    repoFullName, maxRequests, windowMinutes);
        }

        return allowed;
    }

    /**
     * Creates a new Bucket4j token bucket for a repository.
     * Tokens refill at a steady rate over the configured window.
     */
    private Bucket createNewBucket(String repoFullName) {
        // Bandwidth: allow maxRequests per windowMinutes, refilling gradually
        Bandwidth limit = Bandwidth.builder()
                .capacity(maxRequests)
                .refillGreedy(maxRequests, Duration.ofMinutes(windowMinutes))
                .build();
        log.info("Created rate-limit bucket for repo: {}", repoFullName);
        return Bucket.builder().addLimit(limit).build();
    }
}