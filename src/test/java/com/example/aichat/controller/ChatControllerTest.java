package com.example.aichat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.aichat.dto.ChatRequest;
import com.example.aichat.dto.ChatResponse;
import com.example.aichat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ChatService chatService;

    @Test
    void shouldReturn200WhenValidRequest() throws Exception {
        ChatRequest req = new ChatRequest("conv-1", "Sei un assistente", "Ciao");
        when(chatService.processRequest(any())).thenReturn(
                new ChatResponse("conv-1", "Ciao!", "meta/llama-3.2-3b-instruct"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.reply").value("Ciao!"))
                .andExpect(jsonPath("$.model").value("meta/llama-3.2-3b-instruct"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldAcceptRequestWithoutConversationId() throws Exception {
        ChatRequest req = new ChatRequest(null, "Sei un assistente", "Ciao");
        when(chatService.processRequest(any())).thenReturn(
                new ChatResponse(null, "Ciao!", "meta/llama-3.2-3b-instruct"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400WhenQMissing() throws Exception {
        ChatRequest req = new ChatRequest("conv-1", "Sei un assistente", null);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAcceptRequestWithBlankConversationId() throws Exception {
        ChatRequest req = new ChatRequest("", "Sei un assistente", "Ciao");
        when(chatService.processRequest(any())).thenReturn(
                new ChatResponse("", "Ciao!", "meta/llama-3.2-3b-instruct"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400WhenQBlank() throws Exception {
        ChatRequest req = new ChatRequest("conv-1", "Sei un assistente", "");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenInvalidJson() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAcceptRequestWithoutPrompt() throws Exception {
        ChatRequest req = new ChatRequest("conv-1", null, "Ciao");
        when(chatService.processRequest(any())).thenReturn(
                new ChatResponse("conv-1", "Ciao!", "meta/llama-3.2-3b-instruct"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.reply").value("Ciao!"));
    }
}
