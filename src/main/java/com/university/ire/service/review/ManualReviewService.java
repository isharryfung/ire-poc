package com.university.ire.service.review;

import com.university.ire.dto.ManualReviewDto;
import com.university.ire.entity.ManualReview;
import com.university.ire.entity.ReviewStatus;
import com.university.ire.exception.IdentityResolutionException;
import com.university.ire.repository.ManualReviewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualReviewService {

    private final ManualReviewRepository manualReviewRepository;
    private final ReviewQueueManager reviewQueueManager;

    public ManualReviewService(ManualReviewRepository manualReviewRepository, ReviewQueueManager reviewQueueManager) {
        this.manualReviewRepository = manualReviewRepository;
        this.reviewQueueManager = reviewQueueManager;
    }

    public List<ManualReviewDto> getQueue() {
        return reviewQueueManager.getQueue().stream().map(this::toDto).toList();
    }

    public ManualReviewDto getReview(Long id) {
        return toDto(load(id));
    }

    public ManualReview create(String incomingRecordId, String candidateIdentityId, double confidence, String reason) {
        ManualReview review = new ManualReview();
        review.setIncomingRecordId(incomingRecordId);
        review.setCandidateIdentityId(candidateIdentityId);
        review.setConfidence(confidence);
        review.setReason(reason);
        return reviewQueueManager.save(review);
    }

    @Transactional
    public ManualReviewDto approve(Long id) {
        ManualReview review = load(id);
        review.setStatus(ReviewStatus.APPROVED);
        review.setDecision("APPROVED");
        return toDto(reviewQueueManager.save(review));
    }

    @Transactional
    public ManualReviewDto reject(Long id) {
        ManualReview review = load(id);
        review.setStatus(ReviewStatus.REJECTED);
        review.setDecision("REJECTED");
        return toDto(reviewQueueManager.save(review));
    }

    @Transactional
    public ManualReviewDto merge(Long id, String mergeTargetIdentityId) {
        ManualReview review = load(id);
        review.setStatus(ReviewStatus.MERGED);
        review.setDecision("MERGED_TO:" + mergeTargetIdentityId);
        review.setCandidateIdentityId(mergeTargetIdentityId);
        return toDto(reviewQueueManager.save(review));
    }

    private ManualReview load(Long id) {
        return manualReviewRepository.findById(id)
                .orElseThrow(() -> new IdentityResolutionException("Review not found: " + id));
    }

    private ManualReviewDto toDto(ManualReview review) {
        return new ManualReviewDto(
                String.valueOf(review.getId()),
                review.getCandidateIdentityId(),
                review.getIncomingRecordId(),
                review.getConfidence(),
                review.getStatus(),
                review.getReason(),
                review.getDecision());
    }
}
