package org.hkust.ire.db.persistence.service.review;

import org.hkust.ire.db.persistence.domain.ManualReviewDAO;
import org.hkust.ire.db.persistence.repository.ManualReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Manages the manual review queue - adding, listing, and counting pending reviews.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class ReviewQueueManager {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ManualReviewRepository manualReviewRepository;

    /**
     * Adds a new review item to the queue.
     *
     * @param reviewItem the review DAO to add
     * @return saved ManualReviewDAO
     */
    public ManualReviewDAO enqueue(ManualReviewDAO reviewItem) {
        log.info("Enqueueing review: reviewId={}", reviewItem.getReviewId());
        try {
            return manualReviewRepository.save(reviewItem);
        } catch (Exception e) {
            log.error("Error enqueueing review {}: {}", reviewItem.getReviewId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Returns all pending reviews in the queue.
     *
     * @return list of pending ManualReviewDAO objects
     */
    public List<ManualReviewDAO> getPendingReviews() {
        log.debug("Fetching pending reviews");
        try {
            return manualReviewRepository.findByStatus("PENDING");
        } catch (Exception e) {
            log.error("Error fetching pending reviews: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Returns the count of pending reviews.
     *
     * @return number of pending reviews
     */
    public long getPendingCount() {
        try {
            return manualReviewRepository.countByStatus("PENDING");
        } catch (Exception e) {
            log.error("Error counting pending reviews: {}", e.getMessage());
            return 0L;
        }
    }
}
