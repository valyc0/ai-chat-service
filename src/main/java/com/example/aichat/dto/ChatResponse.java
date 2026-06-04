package com.example.aichat.dto;

import java.time.Instant;

public class ChatResponse {

    private String conversationId;
    private String reply;
    private String model;
    private Instant timestamp;

    public ChatResponse() {}

    public ChatResponse(String conversationId, String reply, String model) {
        this.conversationId = conversationId;
        this.reply = reply;
        this.model = model;
        this.timestamp = Instant.now();
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
