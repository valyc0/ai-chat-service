package com.example.aichat.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatClientWrapper {

    private final ChatClient chatClient;

    public ChatClientWrapper(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatClient.ChatClientRequestSpec prompt() {
        return chatClient.prompt();
    }
}
