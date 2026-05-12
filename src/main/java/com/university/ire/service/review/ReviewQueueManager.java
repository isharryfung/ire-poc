package com.university.ire.service.review;

import com.university.ire.entity.ManualReview;
import com.university.ire.entity.ReviewStatus;
import com.university.ire.repository.ManualReviewRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ReviewQueueManager {

    private final ManualReviewRepository manualReviewRepository;

    public ReviewQueueManager(ManualReviewRepository manualReviewRepository) {
        this.manualReviewRepository = manualReviewRepository;
    }

    @Cacheable("review-queue")
    public List<ManualReview> getQueue() {
        return manualReviewRepository.findByStatusOrderByCreatedAtAsc(ReviewStatus.PENDING);
    }

    @CacheEvict(value = "review-queue", allEntries = true)
    public ManualReview save(ManualReview review) {
        return manualReviewRepository.save(review);
    }
}
