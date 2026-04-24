package com.codereviewbot.controller;

import com.codereviewbot.model.ReviewRecord;
import com.codereviewbot.model.WebhookPayload;
import com.codereviewbot.security.WebhookSignatureVerifier;
import com.codereviewbot.service.AIReviewService;
import com.codereviewbot.service.GitHubService;
import com.codereviewbot.service.RateLimiterService;
import com.codereviewbot.service.ReviewLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entry point for all GitHub webhook events.
 *
 * SECURITY CHECKS (in order — fail fast):
 *  1. Signature verification    → reject forged/tampered requests (403)
 *  2. Event type filter         → only handle "pull_request" events (200 + skip)
 *  3. Action filter             → only "opened", "synchronize", "reopened" (200 + skip)
 *  4. Rate limiting             → max N requests per repo per window (429)
 *  5. Input validation          → malformed payloads return 400
 *  Then: fetch diff → AI review → post comment
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final RateLimiterService rateLimiterService;
    private final GitHubService gitHubService;
    private final AIReviewService aiReviewService;
    private final ReviewLogService reviewLogService;
    private final ObjectMapper objectMapper;

    /**
     * Receives GitHub webhook POST requests.
     *
     * @param signatureHeader  X-Hub-Signature-256 — GitHub's HMAC signature of the body
     * @param eventType        X-GitHub-Event — type of event (pull_request, push, etc.)
     * @param rawBody          raw request body bytes (needed for signature verification)
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
        @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
        @RequestBody byte[] rawBody
    ) {
        // ── SECURITY CHECK 1: Verify GitHub signature ───────────────────────
        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            log.warn("Rejected webhook: invalid or missing signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Invalid webhook signature"));
        }

        // ── SECURITY CHECK 2: Only process pull_request events ──────────────
        if (!"pull_request".equals(eventType)) {
            log.info("Skipping non-PR event: {}", eventType);
            return ResponseEntity.ok(Map.of("message", "Event ignored: " + eventType));
        }

        // ── SECURITY CHECK 3: Parse payload (validate JSON structure) ────────
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            log.warn("Malformed webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Malformed payload"));
        }

        // Only act on opened / sync / reopened — not closed/merged
        String action = payload.getAction();
        if (!isReviewableAction(action)) {
            log.info("Skipping PR action: {}", action);
            return ResponseEntity.ok(Map.of("message", "PR action ignored: " + action));
        }

        String repoFullName = payload.getRepository().getFullName();
        int prNumber = payload.getPullRequest().getNumber();
        String prTitle = payload.getPullRequest().getTitle();
        String prAuthor = payload.getPullRequest().getUser().getLogin();

        log.info("Processing PR #{} '{}' in {} by {}", prNumber, prTitle, repoFullName, prAuthor);

        // ── SECURITY CHECK 4: Rate limiting per repository ───────────────────
        if (!rateLimiterService.tryConsume(repoFullName)) {
            ReviewRecord record = new ReviewRecord(
                repoFullName, prNumber, prTitle, prAuthor,
                "Rate limit exceeded — try again later.",
                ReviewRecord.Status.RATE_LIMITED,
                LocalDateTime.now()
            );
            reviewLogService.add(record);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Rate limit exceeded for this repository"));
        }

        // ── CORE FLOW: Fetch diff → AI review → post comment ─────────────────
        try {
            // Step 1: Get the code diff from GitHub
            String diff = gitHubService.fetchPullRequestDiff(repoFullName, prNumber);

            if (diff == null || diff.isBlank()) {
                log.info("Empty diff for PR #{} — skipping review", prNumber);
                return ResponseEntity.ok(Map.of("message", "Empty diff — no review needed"));
            }

            // Step 2: Generate AI review (diff truncation handled inside AIReviewService)
            boolean wasTruncated = diff.length() > 8000; // matches app.max.diff.chars
            String reviewComment = aiReviewService.generateReview(diff, prTitle, repoFullName);

            // Step 3: Post the review comment on the PR
            gitHubService.postReviewComment(repoFullName, prNumber, reviewComment);

            // Step 4: Log the result for the dashboard
            ReviewRecord record = new ReviewRecord(
                repoFullName, prNumber, prTitle, prAuthor,
                reviewComment,
                wasTruncated ? ReviewRecord.Status.DIFF_TOO_LARGE : ReviewRecord.Status.SUCCESS,
                LocalDateTime.now()
            );
            reviewLogService.add(record);

            log.info("Successfully reviewed PR #{} in {}", prNumber, repoFullName);
            return ResponseEntity.ok(Map.of("message", "Review posted successfully"));

        } catch (Exception e) {
            log.error("Error processing PR #{} in {}: {}", prNumber, repoFullName, e.getMessage());

            // Log the failure for the dashboard
            ReviewRecord record = new ReviewRecord(
                repoFullName, prNumber, prTitle, prAuthor,
                "Error: " + e.getMessage(),
                ReviewRecord.Status.ERROR,
                LocalDateTime.now()
            );
            reviewLogService.add(record);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Review processing failed"));
        }
    }

    /** Only review PRs that were opened, updated, or reopened — not closed/merged */
    private boolean isReviewableAction(String action) {
        return "opened".equals(action)
            || "synchronize".equals(action)
            || "reopened".equals(action);
    }
}
