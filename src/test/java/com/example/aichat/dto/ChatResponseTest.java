package com.example.aichat.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatResponseTest {

    @Test
    void shouldCreateWithConstructor() {
        ChatResponse res = new ChatResponse("conv-1", "Ciao!", "model-x");

        assertEquals("conv-1", res.getConversationId());
        assertEquals("Ciao!", res.getReply());
        assertEquals("model-x", res.getModel());
        assertNotNull(res.getTimestamp());
    }

    @Test
    void shouldAllowSetters() {
        ChatResponse res = new ChatResponse();
        res.setConversationId("conv-2");
        res.setReply("Hello");
        res.setModel("model-y");
        res.setTimestamp(null);

        assertEquals("conv-2", res.getConversationId());
        assertEquals("Hello", res.getReply());
        assertEquals("model-y", res.getModel());
        assertNull(res.getTimestamp());
    }
}
