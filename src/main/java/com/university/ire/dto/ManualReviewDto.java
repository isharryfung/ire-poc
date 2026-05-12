package com.university.ire.dto;

import com.university.ire.entity.ReviewStatus;

public record ManualReviewDto(
        String id,
        String candidateIdentityId,
        String incomingRecordId,
        double confidence,
        ReviewStatus status,
        String reason,
        String decision
) {}
