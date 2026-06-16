package com.example.aichat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.example.aichat.dto.ChatRequest;
import com.example.aichat.dto.ChatResponse;
import com.example.aichat.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
class ChatControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String toJson(ChatRequest req) {
        try {
            return objectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // --- Blocking endpoint /api/chat ---

    @Test
    void shouldReturn200WhenValidRequest() {
        when(chatService.processRequest(any())).thenReturn(
                new ChatResponse("conv-1", "Ciao!", "meta/llama-3.2-3b-instruct"));

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(new ChatRequest("conv-1", "Sei un assistente", "Ciao")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.conversationId").isEqualTo("conv-1")
                .jsonPath("$.reply").isEqualTo("Ciao!")
                .jsonPath("$.model").isEqualTo("meta/llama-3.2-3b-instruct")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void shouldAcceptRequestWithoutConversationId() {
        when(chatService.processRequest(any())).thenReturn(
                new ChatResponse(null, "Ciao!", "meta/llama-3.2-3b-instruct"));

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(new ChatRequest(null, "Sei un assistente", "Ciao")))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldReturn400WhenQMissing() {
        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(new ChatRequest("conv-1", "prompt", null)))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenQBlank() {
        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(new ChatRequest("conv-1", "prompt", "")))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenInvalidJson() {
        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("not json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldAcceptRequestWithoutPrompt() {
        when(chatService.processRequest(any())).thenReturn(
                new ChatResponse("conv-1", "Ciao!", "meta/llama-3.2-3b-instruct"));

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(new ChatRequest("conv-1", null, "Ciao")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.conversationId").isEqualTo("conv-1")
                .jsonPath("$.reply").isEqualTo("Ciao!");
    }

    // --- Streaming endpoint /api/chat/stream ---

    @Test
    void shouldStreamTokensWhenValidRequest() {
        when(chatService.streamRequest(any())).thenReturn(Flux.just("Ciao!", "Come", "posso", "aiutarti?"));

        webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(new ChatRequest("conv-1", "Sei un assistente", "Ciao")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE)
                .expectBodyList(String.class)
                .contains("Ciao!", "Come", "posso", "aiutarti?");
    }

    @Test
    void shouldStreamWithoutConversationId() {
        when(chatService.streamRequest(any())).thenReturn(Flux.just("ok"));

        webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(new ChatRequest(null, null, "Ciao")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
