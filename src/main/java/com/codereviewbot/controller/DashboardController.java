package com.codereviewbot.controller;

import com.codereviewbot.model.ReviewRecord;
import com.codereviewbot.service.ReviewLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API endpoints consumed by the frontend dashboard.
 * All responses are JSON — the frontend (Lovable/React) handles rendering.
 *
 * CORS: Configured in AppConfig to allow the frontend's origin.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final ReviewLogService reviewLogService;

    /**
     * Returns all recent review records — used to populate the dashboard table.
     * GET /api/reviews
     */
    @GetMapping("/reviews")
    public List<ReviewRecord> getAllReviews() {
        return reviewLogService.getAll();
    }

    /**
     * Returns summary stats for the dashboard header cards.
     * GET /api/stats
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<ReviewRecord> all = reviewLogService.getAll();

        long success = all.stream().filter(r -> r.getStatus() == ReviewRecord.Status.SUCCESS).count();
        long rateLimited = all.stream().filter(r -> r.getStatus() == ReviewRecord.Status.RATE_LIMITED).count();
        long errors = all.stream().filter(r -> r.getStatus() == ReviewRecord.Status.ERROR).count();
        long truncated = all.stream().filter(r -> r.getStatus() == ReviewRecord.Status.DIFF_TOO_LARGE).count();

        return Map.of(
            "totalReviews",    all.size(),
            "successCount",    success,
            "rateLimitedCount", rateLimited,
            "errorCount",      errors,
            "truncatedCount",  truncated
        );
    }

    /**
     * Health check endpoint for the frontend to confirm the backend is live.
     * GET /api/health
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "codereview-bot");
    }
}
