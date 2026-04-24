package com.codereviewbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps the JSON payload GitHub sends when a PR event fires.
 * We only map fields we actually need — everything else is ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    /** "opened", "synchronize", "reopened", "closed", etc. */
    private String action;

    @JsonProperty("pull_request")
    private PullRequest pullRequest;

    private Repository repository;

    // ── Nested models ───────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequest {
        private int number;
        private String title;

        /** URL of the diff we fetch from GitHub API */
        @JsonProperty("url")
        private String url;

        @JsonProperty("diff_url")
        private String diffUrl;

        @JsonProperty("comments_url")
        private String commentsUrl;

        private User user;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        /** e.g. "Aashish2409/codereview-bot" */
        @JsonProperty("full_name")
        private String fullName;

        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private String login;
    }
}
