package com.codereviewbot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Represents one completed (or failed) PR review event.
 * Stored in memory and exposed via REST for the frontend dashboard.
 */
@Data
@AllArgsConstructor
public class ReviewRecord {

    public enum Status {
        SUCCESS,        // AI review posted successfully
        RATE_LIMITED,   // Request blocked by rate limiter
        DIFF_TOO_LARGE, // Diff exceeded max char limit and was truncated
        ERROR           // Something went wrong (GitHub or Groq API failure)
    }

    /** e.g. "Aashish2409/codereview-bot" */
    private String repoFullName;

    /** PR number, e.g. 42 */
    private int prNumber;

    /** PR title from GitHub payload */
    private String prTitle;

    /** GitHub username who opened the PR */
    private String prAuthor;

    /** The review comment the bot posted (or error message) */
    private String reviewComment;

    /** Result of this review attempt */
    private Status status;

    /** When this review was processed */
    private LocalDateTime processedAt;
}
