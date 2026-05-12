package com.university.ire.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "manual_reviews")
public class ManualReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String incomingRecordId;

    private String candidateIdentityId;

    @Column(nullable = false)
    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(length = 2000)
    private String reason;

    @Column(length = 2000)
    private String decision;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getIncomingRecordId() { return incomingRecordId; }
    public void setIncomingRecordId(String incomingRecordId) { this.incomingRecordId = incomingRecordId; }
    public String getCandidateIdentityId() { return candidateIdentityId; }
    public void setCandidateIdentityId(String candidateIdentityId) { this.candidateIdentityId = candidateIdentityId; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Instant getCreatedAt() { return createdAt; }
}
