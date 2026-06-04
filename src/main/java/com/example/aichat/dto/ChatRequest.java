package com.example.aichat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {

    @Size(max = 100)
    private String conversationId;

    @Size(max = 2000)
    private String prompt;

    @NotBlank(message = "q is required")
    @Size(max = 10000, message = "q must be at most 10000 characters")
    private String q;

    public ChatRequest() {}

    public ChatRequest(String conversationId, String prompt, String q) {
        this.conversationId = conversationId;
        this.prompt = prompt;
        this.q = q;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
}
