package org.hkust.ire.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hkust.ire.db.persistence.service.gateway.ApiGatewayService;
import org.hkust.ire.dto.ApiGatewayRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiGatewayControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiGatewayService apiGatewayService;

    @Test
    @WithMockUser(roles = "API_USER")
    public void testInternalErrorDoesNotLeakRawExceptionMessage() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "test@ust.hk");
        ApiGatewayRequest request = new ApiGatewayRequest("CRM", "CRM-1", payload);

        Mockito.when(apiGatewayService.process(Mockito.any(ApiGatewayRequest.class)))
                .thenThrow(new RuntimeException("sensitive backend error"));

        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}

