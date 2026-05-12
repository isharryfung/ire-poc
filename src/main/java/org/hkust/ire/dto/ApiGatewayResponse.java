package org.hkust.ire.dto;

/**
 * DTO representing an API Gateway response envelope.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class ApiGatewayResponse {

    private boolean success;
    private String message;
    private String goldenId;
    private String matchTier;
    private Double confidenceScore;
    private String status;
    private Object data;

    /** Default constructor. */
    public ApiGatewayResponse() {
    }

    /**
     * Creates a successful resolution response.
     *
     * @param goldenId        resolved golden identity ID
     * @param matchTier       matching tier used
     * @param confidenceScore confidence score
     * @return successful ApiGatewayResponse
     */
    public static ApiGatewayResponse success(String goldenId, String matchTier, Double confidenceScore) {
        ApiGatewayResponse r = new ApiGatewayResponse();
        r.setSuccess(true);
        r.setGoldenId(goldenId);
        r.setMatchTier(matchTier);
        r.setConfidenceScore(confidenceScore);
        r.setStatus("RESOLVED");
        return r;
    }

    /**
     * Creates an error response.
     *
     * @param message error description
     * @return error ApiGatewayResponse
     */
    public static ApiGatewayResponse error(String message) {
        ApiGatewayResponse r = new ApiGatewayResponse();
        r.setSuccess(false);
        r.setMessage(message);
        r.setStatus("ERROR");
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getGoldenId() { return goldenId; }
    public void setGoldenId(String goldenId) { this.goldenId = goldenId; }

    public String getMatchTier() { return matchTier; }
    public void setMatchTier(String matchTier) { this.matchTier = matchTier; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
