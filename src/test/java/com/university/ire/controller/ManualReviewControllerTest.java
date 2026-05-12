package com.university.ire.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.ire.dto.ManualReviewDto;
import com.university.ire.entity.ReviewStatus;
import com.university.ire.service.review.ManualReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManualReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ManualReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManualReviewService manualReviewService;

    @Test
    void shouldApproveReview() throws Exception {
        when(manualReviewService.approve(10L)).thenReturn(
                new ManualReviewDto("10", "2", "src-1", 0.81, ReviewStatus.APPROVED, "reason", "APPROVED"));

        mockMvc.perform(post("/api/v1/reviews/10/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
