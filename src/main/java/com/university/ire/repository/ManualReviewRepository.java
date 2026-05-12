package com.university.ire.repository;

import com.university.ire.entity.ManualReview;
import com.university.ire.entity.ReviewStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualReviewRepository extends JpaRepository<ManualReview, Long> {
    List<ManualReview> findByStatusOrderByCreatedAtAsc(ReviewStatus status);
}
