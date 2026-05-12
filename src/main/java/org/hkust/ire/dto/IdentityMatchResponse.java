package org.hkust.ire.dto;

/**
 * DTO representing an identity match response.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class IdentityMatchResponse {

    private String goldenId;
    private String matchTier;
    private Double confidenceScore;
    private boolean matched;
    private String status;
    private String reviewId;

    /** Default constructor. */
    public IdentityMatchResponse() {
    }

    public String getGoldenId() { return goldenId; }
    public void setGoldenId(String goldenId) { this.goldenId = goldenId; }

    public String getMatchTier() { return matchTier; }
    public void setMatchTier(String matchTier) { this.matchTier = matchTier; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }
}
