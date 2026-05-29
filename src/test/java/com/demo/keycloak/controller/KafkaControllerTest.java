package com.demo.keycloak.controller;

import com.demo.keycloak.messaging.KafkaMessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class KafkaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaMessagePublisher publisher;

    // --- /api/kafka/send ---

    @Test
    void send_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/kafka/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"login\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publisher);
    }

    @Test
    void send_publishesEventAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/kafka/send")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"login\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.key").value("alice"))
                .andExpect(jsonPath("$.action").value("login"))
                .andExpect(jsonPath("$.topic").value("user-events"));

        verify(publisher).publishUserEvent("alice", "login");
    }

    @Test
    void send_usesDefaultActionWhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/kafka/send")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("manual-trigger"));

        verify(publisher).publishUserEvent("alice", "manual-trigger");
    }

    // --- /api/kafka/send-fail ---

    @Test
    void sendFail_publishesFailActionAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/kafka/send-fail")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.note").exists());

        verify(publisher).publishUserEvent("alice", "fail");
    }

    // --- /api/kafka/send-batch ---

    @Test
    void sendBatch_publishesAllEventsAndReturnsCount() throws Exception {
        mockMvc.perform(post("/api/kafka/send-batch")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"action\":\"login\"},{\"action\":\"view\"},{\"action\":\"logout\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.key").value("alice"));

        verify(publisher, times(3)).publishUserEvent(eq("alice"), anyString());
    }

    @Test
    void sendBatch_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/kafka/send-batch")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"action\":\"login\"}]"))
                .andExpect(status().isUnauthorized());
    }

    // --- /api/kafka/send-partition ---

    @Test
    void sendPartition_publishesToSpecificPartitionAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/kafka/send-partition")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"test\",\"partition\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.partition").value("1"));

        verify(publisher).publishToPartition("alice", "test", 1);
    }

    // --- /api/kafka/info ---

    @Test
    void info_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/kafka/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void info_returnsTopicConfiguration() throws Exception {
        mockMvc.perform(get("/api/kafka/info")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("user-events"))
                .andExpect(jsonPath("$.partitions").value(3))
                .andExpect(jsonPath("$.consumerGroup").value("demo-group"))
                .andExpect(jsonPath("$.concept").exists());
    }
}
