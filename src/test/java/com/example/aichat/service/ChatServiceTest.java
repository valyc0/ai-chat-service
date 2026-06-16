package com.example.aichat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import com.example.aichat.dto.ChatRequest;
import com.example.aichat.dto.ChatResponse;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    ChatClientWrapper chatClientWrapper;

    @Mock
    AiClient aiClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Captor
    ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorCaptor;

    private static final String MODEL = "meta/llama-3.2-3b-instruct";

    // --- Blocking processRequest ---

    @Test
    void shouldProcessRequestWithPromptAndQ() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.call(any())).thenReturn("Risposta mockata");

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        ChatResponse response = service.processRequest(new ChatRequest("conv-1", "Sei un AI", "Ciao"));

        assertEquals("conv-1", response.getConversationId());
        assertEquals("Risposta mockata", response.getReply());
        assertEquals(MODEL, response.getModel());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void shouldProcessRequestWithoutPrompt() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.call(any())).thenReturn("Risposta mockata");

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        ChatResponse response = service.processRequest(new ChatRequest("conv-1", null, "Ciao"));

        assertEquals("conv-1", response.getConversationId());
        assertEquals("Risposta mockata", response.getReply());
        verify(requestSpec, never()).system(anyString());
    }

    @Test
    void shouldBlockPassConversationIdToAdvisor() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.call(any())).thenReturn("ok");

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        service.processRequest(new ChatRequest("conv-abc", "prompt", "q"));

        verify(requestSpec).user("q");
        verify(requestSpec).system("prompt");
        verify(requestSpec).advisors(advisorCaptor.capture());

        var advisor = advisorCaptor.getValue();
        var mockAdvisorSpec = mock(ChatClient.AdvisorSpec.class);
        advisor.accept(mockAdvisorSpec);
        verify(mockAdvisorSpec).param("chat_memory_conversation_id", "conv-abc");
    }

    @Test
    void shouldBlockSkipAdvisorWhenConversationIdIsNull() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(aiClient.call(any())).thenReturn("ok");

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        service.processRequest(new ChatRequest(null, "prompt", "q"));

        verify(requestSpec, never()).advisors(any(Consumer.class));
    }

    @Test
    void shouldBlockSanitizeConversationId() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.call(any())).thenReturn("ok");

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        ChatResponse response = service.processRequest(new ChatRequest("conv-1\n*", "prompt", "q"));

        assertEquals("conv-1", response.getConversationId());
    }

    // --- Streaming streamRequest ---

    @Test
    void shouldStreamRequestWithPromptAndQ() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.stream(any())).thenReturn(Flux.just("Ciao!", "Come", "posso", "aiutarti?"));

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        Flux<String> result = service.streamRequest(new ChatRequest("conv-1", "Sei un AI", "Ciao"));

        StepVerifier.create(result)
                .expectNext("Ciao!", "Come", "posso", "aiutarti?")
                .verifyComplete();
    }

    @Test
    void shouldStreamRequestWithoutPrompt() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.stream(any())).thenReturn(Flux.just("ok"));

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        Flux<String> result = service.streamRequest(new ChatRequest("conv-1", null, "Ciao"));

        StepVerifier.create(result)
                .expectNext("ok")
                .verifyComplete();

        verify(requestSpec, never()).system(anyString());
    }

    @Test
    void shouldStreamPassConversationIdToAdvisor() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.stream(any())).thenReturn(Flux.just("ok"));

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        service.streamRequest(new ChatRequest("conv-abc", "prompt", "q"));

        verify(requestSpec).user("q");
        verify(requestSpec).system("prompt");
        verify(requestSpec).advisors(advisorCaptor.capture());

        var advisor = advisorCaptor.getValue();
        var mockAdvisorSpec = mock(ChatClient.AdvisorSpec.class);
        advisor.accept(mockAdvisorSpec);
        verify(mockAdvisorSpec).param("chat_memory_conversation_id", "conv-abc");
    }

    @Test
    void shouldStreamSkipAdvisorWhenConversationIdIsNull() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(aiClient.stream(any())).thenReturn(Flux.just("ok"));

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        service.streamRequest(new ChatRequest(null, "prompt", "q"));

        verify(requestSpec, never()).advisors(any(Consumer.class));
    }

    @Test
    void shouldStreamSanitizeConversationId() {
        when(chatClientWrapper.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(aiClient.stream(any())).thenReturn(Flux.just("ok"));

        ChatService service = new ChatService(chatClientWrapper, aiClient, MODEL);
        Flux<String> result = service.streamRequest(new ChatRequest("conv-1\n*", "prompt", "q"));

        StepVerifier.create(result)
                .expectNext("ok")
                .verifyComplete();

        verify(requestSpec).user("q");
    }
}
