package org.hkust.ire.db.persistence.service.review;

import org.hkust.ire.common.utils.GeneralUtil;
import org.hkust.ire.db.persistence.domain.AuditLogDAO;
import org.hkust.ire.db.persistence.domain.ManualReviewDAO;
import org.hkust.ire.db.persistence.repository.AuditLogRepository;
import org.hkust.ire.db.persistence.repository.ManualReviewRepository;
import org.hkust.ire.dto.ManualReviewDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service managing the manual review workflow for identity resolution decisions.
 *
 * <p>Handles approve, reject, and merge actions with full audit trail.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class ManualReviewService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ManualReviewRepository manualReviewRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ReviewQueueManager reviewQueueManager;

    /**
     * Creates a new manual review item.
     *
     * @param incomingPayload  the serialized raw payload
     * @param sourceSystem     originating source system
     * @param confidenceScore  the confidence score that triggered review
     * @param candidateGoldenId candidate golden ID if any
     * @return the created ManualReviewDAO
     */
    @Transactional
    public ManualReviewDAO createReview(String incomingPayload, String sourceSystem,
                                        Double confidenceScore, String candidateGoldenId) {
        log.info("Creating manual review for sourceSystem={}", sourceSystem);
        try {
            String reviewId = "REV-" + GeneralUtil.generateUuid().substring(0, 8).toUpperCase();
            ManualReviewDAO review = new ManualReviewDAO(reviewId, incomingPayload, sourceSystem);
            review.setConfidenceScore(confidenceScore);
            review.setCandidateGoldenId(candidateGoldenId);
            ManualReviewDAO saved = reviewQueueManager.enqueue(review);
            log.info("Manual review created: reviewId={}", reviewId);
            return saved;
        } catch (Exception e) {
            log.error("Error creating manual review: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Approves a manual review decision.
     *
     * @param reviewId  the review ID
     * @param reviewer  the reviewer's identifier
     * @param notes     review notes
     * @return updated ManualReviewDAO
     */
    @Transactional
    public ManualReviewDAO approve(String reviewId, String reviewer, String notes) {
        log.info("Approving review: reviewId={}, reviewer={}", reviewId, reviewer);
        try {
            Optional<ManualReviewDAO> opt = manualReviewRepository.findByReviewId(reviewId);
            if (!opt.isPresent()) {
                throw new RuntimeException("Review not found: " + reviewId);
            }
            ManualReviewDAO review = opt.get();
            review.setStatus("APPROVED");
            review.setReviewer(reviewer);
            review.setReviewNotes(notes);
            review.setReviewedDate(new Date());
            ManualReviewDAO saved = manualReviewRepository.save(review);

            AuditLogDAO audit = new AuditLogDAO("REVIEW_APPROVED", "MANUAL_REVIEW", reviewId);
            audit.setPerformedBy(reviewer);
            audit.setDetails(notes);
            auditLogRepository.save(audit);

            log.info("Review approved: reviewId={}", reviewId);
            return saved;
        } catch (Exception e) {
            log.error("Error approving review {}: {}", reviewId, e.getMessage());
            throw e;
        }
    }

    /**
     * Rejects a manual review decision.
     *
     * @param reviewId the review ID
     * @param reviewer the reviewer's identifier
     * @param notes    rejection reason
     * @return updated ManualReviewDAO
     */
    @Transactional
    public ManualReviewDAO reject(String reviewId, String reviewer, String notes) {
        log.info("Rejecting review: reviewId={}, reviewer={}", reviewId, reviewer);
        try {
            Optional<ManualReviewDAO> opt = manualReviewRepository.findByReviewId(reviewId);
            if (!opt.isPresent()) {
                throw new RuntimeException("Review not found: " + reviewId);
            }
            ManualReviewDAO review = opt.get();
            review.setStatus("REJECTED");
            review.setReviewer(reviewer);
            review.setReviewNotes(notes);
            review.setReviewedDate(new Date());
            ManualReviewDAO saved = manualReviewRepository.save(review);

            AuditLogDAO audit = new AuditLogDAO("REVIEW_REJECTED", "MANUAL_REVIEW", reviewId);
            audit.setPerformedBy(reviewer);
            audit.setDetails(notes);
            auditLogRepository.save(audit);

            return saved;
        } catch (Exception e) {
            log.error("Error rejecting review {}: {}", reviewId, e.getMessage());
            throw e;
        }
    }

    /**
     * Returns all pending reviews as DTOs.
     *
     * @return list of ManualReviewDTO
     */
    public List<ManualReviewDAO> getPendingReviews() {
        try {
            return reviewQueueManager.getPendingReviews();
        } catch (Exception e) {
            log.error("Error getting pending reviews: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Finds a review by its ID.
     *
     * @param reviewId the review ID
     * @return optional ManualReviewDAO
     */
    public Optional<ManualReviewDAO> findByReviewId(String reviewId) {
        try {
            return manualReviewRepository.findByReviewId(reviewId);
        } catch (Exception e) {
            log.error("Error finding review {}: {}", reviewId, e.getMessage());
            throw e;
        }
    }
}
