package com.example.aichat.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    @Retry(name = "aiChat")
    @CircuitBreaker(name = "aiChat", fallbackMethod = "fallback")
    public String call(ChatClient.ChatClientRequestSpec spec) {
        String reply = spec.call().content();
        if (reply == null) {
            throw new RuntimeException("AI returned null content");
        }
        return reply;
    }

    public String fallback(ChatClient.ChatClientRequestSpec spec, Throwable t) {
        log.error("AI call failed after retries", t);
        return "The AI service is currently unavailable. Please try again later.";
    }
}
