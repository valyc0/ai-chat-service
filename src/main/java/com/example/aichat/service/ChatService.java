package com.example.aichat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.aichat.dto.ChatRequest;
import com.example.aichat.dto.ChatResponse;

import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClientWrapper chatClient;
    private final AiClient aiClient;
    private final String model;

    public ChatService(ChatClientWrapper chatClient, AiClient aiClient,
                       @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.chatClient = chatClient;
        this.aiClient = aiClient;
        this.model = model;
    }

    public ChatResponse processRequest(ChatRequest request) {
        String convId = sanitize(request.getConversationId());

        MDC.put("conversationId", convId != null ? convId : "none");
        if (request.getQ() != null) {
            MDC.put("queryLength", String.valueOf(request.getQ().length()));
        }

        try {
            log.info("Processing chat request");

            var spec = chatClient.prompt().user(request.getQ());
            if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
                spec = spec.system(request.getPrompt());
            }
            if (convId != null && !convId.isBlank()) {
                spec = spec.advisors(a -> a.param("chat_memory_conversation_id", convId));
            }

            String reply = aiClient.call(spec);

            MDC.put("replyLength", String.valueOf(reply.length()));
            log.info("Chat request completed");

            return new ChatResponse(convId, reply, model);
        } finally {
            MDC.clear();
        }
    }

    public Flux<String> streamRequest(ChatRequest request) {
        String convId = sanitize(request.getConversationId());

        MDC.put("conversationId", convId != null ? convId : "none");
        if (request.getQ() != null) {
            MDC.put("queryLength", String.valueOf(request.getQ().length()));
        }

        log.info("Processing streaming chat request");

        var spec = chatClient.prompt().user(request.getQ());
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            spec = spec.system(request.getPrompt());
        }
        if (convId != null && !convId.isBlank()) {
            spec = spec.advisors(a -> a.param("chat_memory_conversation_id", convId));
        }

        Flux<String> stream = aiClient.stream(spec);

        return stream
                .doFinally(signalType -> {
                    MDC.remove("conversationId");
                    MDC.remove("queryLength");
                    log.info("Streaming chat request completed");
                });
    }

    private static String sanitize(String conversationId) {
        if (conversationId == null) return null;
        return conversationId.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
