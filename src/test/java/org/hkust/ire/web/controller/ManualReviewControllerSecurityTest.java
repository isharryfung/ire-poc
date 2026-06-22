package org.hkust.ire.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hkust.ire.db.persistence.domain.ManualReviewDAO;
import org.hkust.ire.db.persistence.service.review.ManualReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ManualReviewControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManualReviewService manualReviewService;

    @Test
    @WithMockUser(roles = "API_USER")
    public void testApproveEndpointForbiddenForApiUser() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("reviewer", "tester");
        body.put("notes", "approve");

        mockMvc.perform(post("/api/v1/reviews/REV-123/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REVIEWER")
    public void testApproveEndpointAllowedForReviewer() throws Exception {
        ManualReviewDAO reviewDAO = new ManualReviewDAO("REV-123", "payload", "CRM");
        when(manualReviewService.approve(anyString(), anyString(), anyString())).thenReturn(reviewDAO);

        Map<String, String> body = new HashMap<>();
        body.put("reviewer", "reviewer1");
        body.put("notes", "approved");

        mockMvc.perform(post("/api/v1/reviews/REV-123/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value("REV-123"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
