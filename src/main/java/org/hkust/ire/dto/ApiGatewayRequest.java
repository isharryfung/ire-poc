package org.hkust.ire.dto;

import java.util.Map;

/**
 * DTO representing an inbound API Gateway ingestion request.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class ApiGatewayRequest {

    private String sourceSystem;
    private String sourceId;
    private Map<String, Object> payload;
    private String requestId;
    private String timestamp;

    /** Default constructor. */
    public ApiGatewayRequest() {
    }

    /**
     * Parameterized constructor.
     *
     * @param sourceSystem the source system name
     * @param sourceId     the source record ID
     * @param payload      the raw payload map
     */
    public ApiGatewayRequest(String sourceSystem, String sourceId, Map<String, Object> payload) {
        this.sourceSystem = sourceSystem;
        this.sourceId = sourceId;
        this.payload = payload;
    }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
