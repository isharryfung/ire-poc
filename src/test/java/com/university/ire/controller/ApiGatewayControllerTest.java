package com.university.ire.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.ire.dto.ApiGatewayRequest;
import com.university.ire.dto.ApiGatewayResponse;
import com.university.ire.service.gateway.ApiGatewayService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApiGatewayController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiGatewayService apiGatewayService;

    @Test
    void shouldIngestPayload() throws Exception {
        when(apiGatewayService.ingest(any())).thenReturn(
                new ApiGatewayResponse("1", "AUTO_MERGE", "TIER_1", 1.0, null, "ok"));

        ApiGatewayRequest request = new ApiGatewayRequest(
                "EVENT_SYSTEM",
                "evt-1",
                Instant.parse("2026-05-12T12:00:00Z"),
                objectMapper.valueToTree(Map.of("email", "test@hkust.edu.hk", "name", "Test User")));

        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("AUTO_MERGE"))
                .andExpect(jsonPath("$.tier").value("TIER_1"));
    }
}
