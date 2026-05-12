package com.university.ire.controller;

import com.university.ire.dto.ManualReviewDto;
import com.university.ire.service.review.ManualReviewService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
public class ManualReviewController {

    private final ManualReviewService manualReviewService;

    public ManualReviewController(ManualReviewService manualReviewService) {
        this.manualReviewService = manualReviewService;
    }

    @GetMapping
    public List<ManualReviewDto> getQueue() {
        return manualReviewService.getQueue();
    }

    @GetMapping("/{id}")
    public ManualReviewDto get(@PathVariable Long id) {
        return manualReviewService.getReview(id);
    }

    @PostMapping("/{id}/approve")
    public ManualReviewDto approve(@PathVariable Long id) {
        return manualReviewService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public ManualReviewDto reject(@PathVariable Long id) {
        return manualReviewService.reject(id);
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<ManualReviewDto> merge(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String target = body == null ? null : body.getOrDefault("targetIdentityId", "");
        return ResponseEntity.ok(manualReviewService.merge(id, target));
    }
}
