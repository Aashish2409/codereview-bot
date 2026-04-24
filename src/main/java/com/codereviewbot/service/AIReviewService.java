package com.codereviewbot.service;

import com.codereviewbot.util.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Sends the PR diff to Groq's LLaMA model and retrieves the AI-generated review.
 *
 * API used: Groq's OpenAI-compatible /v1/chat/completions endpoint
 * Model: llama3-70b-8192 (fast and capable — you already used this in CIPHER!)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIReviewService {

    private final WebClient webClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.model}")
    private String groqModel;

    /**
     * Sends the diff to Groq and returns the AI review as a markdown string.
     *
     * @param diff      the raw git diff from GitHub
     * @param prTitle   the PR title
     * @param repoName  the repository name
     * @return the AI-generated review comment in markdown
     * @throws RuntimeException if the Groq API call fails
     */
    public String generateReview(String diff, String prTitle, String repoName) {
        log.info("Sending diff to Groq for repo: {} PR: {}", repoName, prTitle);

        // Build the JSON request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", groqModel);
        requestBody.put("max_tokens", 1500);
        requestBody.put("temperature", 0.3); // lower = more consistent, deterministic output

        // Messages array: system prompt + user prompt
        ArrayNode messages = requestBody.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", promptBuilder.buildSystemPrompt());

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", promptBuilder.buildUserPrompt(diff, prTitle, repoName));

        try {
            // Call Groq API
            String responseBody = webClient.post()
                .uri(groqApiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block(); // blocking is fine here — we're in a webhook handler thread

            // Parse the response and extract the message content
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String review = responseJson
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

            log.info("Groq review generated successfully for repo: {}", repoName);
            return review;

        } catch (Exception e) {
            log.error("Groq API call failed for repo {}: {}", repoName, e.getMessage());
            throw new RuntimeException("AI review generation failed: " + e.getMessage(), e);
        }
    }
}
