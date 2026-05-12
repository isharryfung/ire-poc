package com.university.ire.controller;

import com.university.ire.repository.ManualReviewRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    private final ManualReviewRepository manualReviewRepository;

    public HealthCheckController(ManualReviewRepository manualReviewRepository) {
        this.manualReviewRepository = manualReviewRepository;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "reviewQueueDepth", manualReviewRepository.findByStatusOrderByCreatedAtAsc(com.university.ire.entity.ReviewStatus.PENDING).size());
    }
}
