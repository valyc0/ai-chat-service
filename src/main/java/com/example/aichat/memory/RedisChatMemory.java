package com.example.aichat.memory;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);
    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChatMemory(StringRedisTemplate redis,
                           @Value("${chat.memory.ttl:4h}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
    }

    record MessageRecord(String role, String content) {}

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + sanitize(conversationId);
        for (Message msg : messages) {
            String role;
            if (msg instanceof UserMessage) role = "user";
            else if (msg instanceof AssistantMessage) role = "assistant";
            else if (msg instanceof SystemMessage) role = "system";
            else role = "user";
            try {
                String json = objectMapper.writeValueAsString(
                    new MessageRecord(role, msg.getText()));
                redis.opsForList().rightPush(key, json);
                redis.expire(key, ttl);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize message for conversationId: {}", conversationId, e);
                throw new RuntimeException("Failed to serialize message", e);
            }
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = KEY_PREFIX + sanitize(conversationId);
        List<String> jsonList = redis.opsForList().range(key, -lastN, -1);
        if (jsonList == null) return List.of();

        return jsonList.stream().map(json -> {
            try {
                MessageRecord r = objectMapper.readValue(json, MessageRecord.class);
                return (Message) switch (r.role()) {
                    case "assistant" -> new AssistantMessage(r.content());
                    case "system" -> new SystemMessage(r.content());
                    default -> new UserMessage(r.content());
                };
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize message for conversationId: {}", conversationId, e);
                throw new RuntimeException("Failed to deserialize message", e);
            }
        }).collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        redis.delete(KEY_PREFIX + sanitize(conversationId));
    }

    private static String sanitize(String conversationId) {
        if (conversationId == null) return "";
        return conversationId.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
