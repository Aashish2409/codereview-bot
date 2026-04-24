package com.codereviewbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the CodeReview Bot Spring Boot application.
 * This bot listens to GitHub PR webhook events and posts AI-generated
 * code reviews using Groq's LLaMA model.
 */
@SpringBootApplication
public class CodeReviewBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewBotApplication.class, args);
    }
}
