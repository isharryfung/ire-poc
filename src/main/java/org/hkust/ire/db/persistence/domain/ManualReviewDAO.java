package org.hkust.ire.db.persistence.domain;

import javax.persistence.*;
import java.util.Date;

/**
 * DAO representing a manual review item in the review queue.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Entity
@Table(name = "MANUAL_REVIEWS")
public class ManualReviewDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REVIEW_ID", nullable = false, unique = true)
    private String reviewId;

    @Column(name = "INCOMING_PAYLOAD", columnDefinition = "TEXT")
    private String incomingPayload;

    @Column(name = "CANDIDATE_GOLDEN_ID")
    private String candidateGoldenId;

    @Column(name = "CONFIDENCE_SCORE")
    private Double confidenceScore;

    @Column(name = "SOURCE_SYSTEM")
    private String sourceSystem;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "REVIEWER")
    private String reviewer;

    @Column(name = "REVIEW_NOTES", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "CREATED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @Column(name = "REVIEWED_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date reviewedDate;

    /** Default constructor. */
    public ManualReviewDAO() {
    }

    /**
     * Parameterized constructor.
     *
     * @param reviewId        unique review ID
     * @param incomingPayload raw payload that triggered review
     * @param sourceSystem    originating source system
     */
    public ManualReviewDAO(String reviewId, String incomingPayload, String sourceSystem) {
        this.reviewId = reviewId;
        this.incomingPayload = incomingPayload;
        this.sourceSystem = sourceSystem;
        this.status = "PENDING";
        this.createdDate = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getIncomingPayload() { return incomingPayload; }
    public void setIncomingPayload(String incomingPayload) { this.incomingPayload = incomingPayload; }

    public String getCandidateGoldenId() { return candidateGoldenId; }
    public void setCandidateGoldenId(String candidateGoldenId) { this.candidateGoldenId = candidateGoldenId; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Date getReviewedDate() { return reviewedDate; }
    public void setReviewedDate(Date reviewedDate) { this.reviewedDate = reviewedDate; }
}
