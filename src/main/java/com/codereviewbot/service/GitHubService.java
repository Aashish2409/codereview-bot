package com.codereviewbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Handles all communication with the GitHub REST API:
 *  1. Fetching the PR diff (what code actually changed)
 *  2. Posting the AI review as a comment on the PR
 *
 * SECURITY NOTE:
 *  We use a fine-grained Personal Access Token (PAT) scoped to only
 *  "pull_requests: write" permission — minimum required privilege.
 *  This limits damage if the token is ever leaked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${github.token}")
    private String githubToken;

    @Value("${github.api.base-url}")
    private String githubApiBaseUrl;

    /**
     * Fetches the raw unified diff for a pull request.
     * GitHub returns the diff when we request with Accept: application/vnd.github.v3.diff
     *
     * @param repoFullName  e.g. "Aashish2409/codereview-bot"
     * @param prNumber      the PR number, e.g. 42
     * @return the raw diff string
     */
    public String fetchPullRequestDiff(String repoFullName, int prNumber) {
        String url = githubApiBaseUrl + "/repos/" + repoFullName + "/pulls/" + prNumber;
        log.info("Fetching diff for PR #{} in {}", prNumber, repoFullName);

        try {
            return webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        } catch (Exception e) {
            log.error("Failed to fetch diff for PR #{} in {}: {}", prNumber, repoFullName, e.getMessage());
            throw new RuntimeException("Could not fetch PR diff from GitHub: " + e.getMessage(), e);
        }
    }

    /**
     * Posts the AI-generated review as a comment on the PR.
     * The comment is prefixed with a bot header so it's clearly identified.
     *
     * @param repoFullName  e.g. "Aashish2409/codereview-bot"
     * @param prNumber      the PR number
     * @param reviewText    the markdown review from Groq
     */
    public void postReviewComment(String repoFullName, int prNumber, String reviewText) {
        String url = githubApiBaseUrl + "/repos/" + repoFullName + "/issues/" + prNumber + "/comments";

        // Add a bot header so the comment is clearly from our bot
        String commentBody = buildCommentBody(reviewText);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("body", commentBody);

        log.info("Posting review comment on PR #{} in {}", prNumber, repoFullName);

        try {
            webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

            log.info("Review comment posted successfully on PR #{}", prNumber);

        } catch (Exception e) {
            log.error("Failed to post comment on PR #{} in {}: {}", prNumber, repoFullName, e.getMessage());
            throw new RuntimeException("Could not post review comment to GitHub: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps the AI review with a bot header and footer for clarity on GitHub.
     */
    private String buildCommentBody(String reviewText) {
        return """
            ## 🤖 AI Code Review
            > *Automated review powered by Groq LLaMA 3 via CodeReview Bot*
            
            ---
            
            %s
            
            ---
            *This review was generated automatically. Please use your own judgment.*
            """.formatted(reviewText);
    }
}
