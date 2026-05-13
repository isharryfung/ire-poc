package org.hkust.ire.dto;

/**
 * DTO for transferring manual review data to/from clients.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class ManualReviewDTO {

    private String reviewId;
    private String status;
    private String sourceSystem;
    private Double confidenceScore;
    private String candidateGoldenId;
    private String reviewNotes;
    private String reviewer;
    private String createdDate;
    private String reviewedDate;

    /** Default constructor. */
    public ManualReviewDTO() {
    }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getCandidateGoldenId() { return candidateGoldenId; }
    public void setCandidateGoldenId(String candidateGoldenId) { this.candidateGoldenId = candidateGoldenId; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public String getReviewedDate() { return reviewedDate; }
    public void setReviewedDate(String reviewedDate) { this.reviewedDate = reviewedDate; }
}
