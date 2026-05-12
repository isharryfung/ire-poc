package org.hkust.ire.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hkust.ire.dto.ApiGatewayRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for ApiGatewayController.
 *
 * @author ire-team
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ApiGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "API_USER")
    public void testIngestEndpointReturns200() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "test.ingestion@ust.hk");
        payload.put("firstName", "Test");
        payload.put("lastName", "Ingestion");

        ApiGatewayRequest request = new ApiGatewayRequest("CRM", "CRM-TEST-001", payload);
        request.setRequestId("REQ-001");

        mockMvc.perform(post("/api/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void testIngestEndpointRequiresAuth() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "unauth@ust.hk");

        ApiGatewayRequest request = new ApiGatewayRequest("CRM", "CRM-UNAUTH", payload);

        mockMvc.perform(post("/api/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
