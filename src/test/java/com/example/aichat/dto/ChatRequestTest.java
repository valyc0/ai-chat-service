package com.example.aichat.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatRequestTest {

    @Test
    void shouldCreateWithAllFields() {
        ChatRequest req = new ChatRequest("conv-1", "prompt", "q");

        assertEquals("conv-1", req.getConversationId());
        assertEquals("prompt", req.getPrompt());
        assertEquals("q", req.getQ());
    }

    @Test
    void shouldAllowSetters() {
        ChatRequest req = new ChatRequest();
        req.setConversationId("conv-2");
        req.setPrompt("system");
        req.setQ("query");

        assertEquals("conv-2", req.getConversationId());
        assertEquals("system", req.getPrompt());
        assertEquals("query", req.getQ());
    }
}
