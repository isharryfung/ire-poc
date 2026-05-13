package org.hkust.ire.web.controller;

import org.hkust.ire.db.persistence.domain.ManualReviewDAO;
import org.hkust.ire.db.persistence.service.review.ManualReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for manual review queue management.
 *
 * <p>Provides both REST endpoints and JSP view for reviewing identity decisions.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Controller
@RequestMapping
public class ManualReviewController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ManualReviewService manualReviewService;

    /**
     * Renders the review queue JSP view.
     *
     * @param model Spring model
     * @return review/queue view
     */
    @GetMapping("/review/queue")
    public String reviewQueueView(Model model) {
        log.debug("Loading review queue view");
        try {
            List<ManualReviewDAO> reviews = manualReviewService.getPendingReviews();
            model.addAttribute("reviews", reviews);
        } catch (Exception e) {
            log.error("Error loading review queue: {}", e.getMessage());
        }
        return "review/queue";
    }

    /**
     * Returns the current review queue as JSON.
     *
     * @return list of pending ManualReviewDAO
     */
    @GetMapping("/api/v1/reviews")
    @ResponseBody
    public ResponseEntity<List<ManualReviewDAO>> getQueue() {
        log.debug("Fetching review queue");
        try {
            return ResponseEntity.ok(manualReviewService.getPendingReviews());
        } catch (Exception e) {
            log.error("Error fetching review queue: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Returns a specific review by ID.
     *
     * @param reviewId the review ID
     * @return ManualReviewDAO or 404
     */
    @GetMapping("/api/v1/reviews/{reviewId}")
    @ResponseBody
    public ResponseEntity<ManualReviewDAO> getReview(@PathVariable String reviewId) {
        try {
            Optional<ManualReviewDAO> opt = manualReviewService.findByReviewId(reviewId);
            return opt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching review {}: {}", reviewId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Approves a manual review.
     *
     * @param reviewId the review ID
     * @param body     request body with reviewer and notes
     * @return updated ManualReviewDAO
     */
    @PostMapping("/api/v1/reviews/{reviewId}/approve")
    @ResponseBody
    public ResponseEntity<ManualReviewDAO> approve(@PathVariable String reviewId,
                                                    @RequestBody Map<String, String> body) {
        log.info("Approving review: {}", reviewId);
        try {
            String reviewer = body.getOrDefault("reviewer", "SYSTEM");
            String notes = body.getOrDefault("notes", "");
            return ResponseEntity.ok(manualReviewService.approve(reviewId, reviewer, notes));
        } catch (Exception e) {
            log.error("Error approving review {}: {}", reviewId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Rejects a manual review.
     *
     * @param reviewId the review ID
     * @param body     request body with reviewer and notes
     * @return updated ManualReviewDAO
     */
    @PostMapping("/api/v1/reviews/{reviewId}/reject")
    @ResponseBody
    public ResponseEntity<ManualReviewDAO> reject(@PathVariable String reviewId,
                                                   @RequestBody Map<String, String> body) {
        log.info("Rejecting review: {}", reviewId);
        try {
            String reviewer = body.getOrDefault("reviewer", "SYSTEM");
            String notes = body.getOrDefault("notes", "");
            return ResponseEntity.ok(manualReviewService.reject(reviewId, reviewer, notes));
        } catch (Exception e) {
            log.error("Error rejecting review {}: {}", reviewId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
