package com.example.aichat.memory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ListOperations<String, String> listOps;

    @Captor
    ArgumentCaptor<Duration> durationCaptor;

    RedisChatMemory memory;

    @BeforeEach
    void setUp() {
        memory = new RedisChatMemory(redis, Duration.ofHours(4));
    }

    @Test
    void shouldAddMessageToRedis() {
        when(redis.opsForList()).thenReturn(listOps);
        List<Message> msgs = List.of(new UserMessage("Ciao"));
        memory.add("conv-1", msgs);

        verify(listOps).rightPush("chat:memory:conv-1",
                "{\"role\":\"user\",\"content\":\"Ciao\"}");
        verify(redis).expire(eq("chat:memory:conv-1"), durationCaptor.capture());
        assertTrue(durationCaptor.getValue().toSeconds() > 0);
    }

    @Test
    void shouldAddMultipleMessages() {
        when(redis.opsForList()).thenReturn(listOps);
        List<Message> msgs = List.of(
                new UserMessage("Ciao"),
                new AssistantMessage("Ehilà"));
        memory.add("conv-1", msgs);

        verify(listOps).rightPush("chat:memory:conv-1",
                "{\"role\":\"user\",\"content\":\"Ciao\"}");
        verify(listOps).rightPush("chat:memory:conv-1",
                "{\"role\":\"assistant\",\"content\":\"Ehilà\"}");
        verify(redis, atLeastOnce()).expire(eq("chat:memory:conv-1"), durationCaptor.capture());
        assertTrue(durationCaptor.getValue().toSeconds() > 0);
    }

    @Test
    void shouldRetrieveMessagesFromRedis() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range("chat:memory:conv-1", -10, -1)).thenReturn(List.of(
                "{\"role\":\"user\",\"content\":\"Ciao\"}",
                "{\"role\":\"assistant\",\"content\":\"Ehilà\"}"));

        List<Message> result = memory.get("conv-1", 10);

        assertEquals(2, result.size());
        assertInstanceOf(UserMessage.class, result.get(0));
        assertEquals("Ciao", result.get(0).getText());
        assertInstanceOf(AssistantMessage.class, result.get(1));
        assertEquals("Ehilà", result.get(1).getText());
    }

    @Test
    void shouldReturnEmptyListWhenNoMessages() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range("chat:memory:empty", -10, -1)).thenReturn(null);

        List<Message> result = memory.get("empty", 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldClearConversationFromRedis() {
        memory.clear("conv-1");

        verify(redis).delete("chat:memory:conv-1");
    }

    @Test
    void shouldRetrieveOnlyLastN() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range("chat:memory:conv-1", -3, -1)).thenReturn(List.of(
                "{\"role\":\"user\",\"content\":\"msg4\"}",
                "{\"role\":\"assistant\",\"content\":\"msg5\"}",
                "{\"role\":\"user\",\"content\":\"msg6\"}"));

        List<Message> result = memory.get("conv-1", 3);

        assertEquals(3, result.size());
        assertEquals("msg4", result.get(0).getText());
        assertEquals("msg6", result.get(2).getText());
        verify(listOps).range("chat:memory:conv-1", -3, -1);
    }

    @Test
    void shouldSanitizeConversationIdInAdd() {
        when(redis.opsForList()).thenReturn(listOps);
        List<Message> msgs = List.of(new UserMessage("test"));
        memory.add("conv\n*", msgs);

        verify(listOps).rightPush("chat:memory:conv", "{\"role\":\"user\",\"content\":\"test\"}");
    }

    @Test
    void shouldSanitizeConversationIdInGet() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range("chat:memory:conv", -10, -1)).thenReturn(List.of());

        memory.get("conv\n*", 10);

        verify(listOps).range("chat:memory:conv", -10, -1);
    }

    @Test
    void shouldSanitizeConversationIdInClear() {
        memory.clear("conv\n*");

        verify(redis).delete("chat:memory:conv");
    }
}
