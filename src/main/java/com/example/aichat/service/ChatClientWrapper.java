package com.example.aichat.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

@Component
public class ChatClientWrapper {

    private final ChatClient chatClient;

    public ChatClientWrapper(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatClient.ChatClientRequestSpec prompt() {
        return chatClient.prompt();
    }

    public ChatClient.ChatClientRequestSpec stream() {
        return chatClient.prompt();
    }
}
