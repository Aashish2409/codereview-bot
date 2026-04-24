package com.codereviewbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Core application configuration:
 *  - WebClient (HTTP client for GitHub + Groq API calls)
 *  - ObjectMapper (JSON serialization with Java 8 date/time support)
 *  - CORS (allows the React frontend to call our API)
 */
@Configuration
public class AppConfig {

    /**
     * WebClient is Spring's modern, non-blocking HTTP client.
     * We use it for GitHub and Groq API calls.
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .codecs(config -> config
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024) // 10MB — handles large diffs
            )
            .build();
    }

    /**
     * ObjectMapper with JavaTimeModule so LocalDateTime serializes as ISO-8601
     * (e.g. "2024-11-15T14:30:00") — readable by the React frontend.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * CORS configuration — allows the React frontend (on any origin during dev,
     * or your specific Vercel URL in production) to call our Spring Boot API.
     *
     * IMPORTANT: In production, replace "*" with your actual frontend URL
     * e.g. "https://your-app.vercel.app"
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("*") // TODO: lock down to your Vercel URL in production
                    .allowedMethods("GET", "OPTIONS")
                    .allowedHeaders("*")
                    .maxAge(3600);
            }
        };
    }
}
