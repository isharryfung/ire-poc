package org.hkust.ire.db.persistence.service.gateway;

import org.hkust.ire.db.persistence.service.identity.IdentityResolutionService;
import org.hkust.ire.dto.ApiGatewayRequest;
import org.hkust.ire.dto.ApiGatewayResponse;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * API Gateway service that acts as the unified ingestion point for all source systems.
 *
 * <p>Orchestrates payload validation, parsing, source detection, and identity resolution.
 * Supports Event system, Attendance, and 3rd-party form payloads.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class ApiGatewayService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private PayloadValidator payloadValidator;

    @Autowired
    private DynamicPayloadParser dynamicPayloadParser;

    @Autowired
    private SourceSystemMapper sourceSystemMapper;

    @Autowired
    private IdentityResolutionService identityResolutionService;

    /**
     * Processes an incoming identity ingestion request.
     *
     * <p>Pipeline: validate → detect source → parse to canonical → resolve identity</p>
     *
     * @param request the API gateway request
     * @return ApiGatewayResponse with resolution result
     */
    public ApiGatewayResponse process(ApiGatewayRequest request) {
        log.info("Processing ingest request from sourceSystem={}, requestId={}",
                request.getSourceSystem(), request.getRequestId());
        try {
            String detectedSource = sourceSystemMapper.detectSourceSystem(
                    request.getSourceSystem(), request.getPayload());
            request.setSourceSystem(detectedSource);

            payloadValidator.validate(detectedSource, request.getPayload());

            CanonicalIdentity canonical = dynamicPayloadParser.parse(detectedSource, request.getPayload());
            canonical.setSourceId(request.getSourceId());

            IdentityMatchResponse matchResponse = identityResolutionService.resolve(canonical);

            if (matchResponse.isMatched()) {
                log.info("Identity resolved: goldenId={}, tier={}, score={}",
                        matchResponse.getGoldenId(), matchResponse.getMatchTier(), matchResponse.getConfidenceScore());
                return ApiGatewayResponse.success(
                        matchResponse.getGoldenId(),
                        matchResponse.getMatchTier(),
                        matchResponse.getConfidenceScore());
            } else {
                ApiGatewayResponse response = new ApiGatewayResponse();
                response.setSuccess(true);
                response.setStatus("REVIEW_REQUIRED");
                response.setMatchTier(matchResponse.getMatchTier());
                response.setConfidenceScore(matchResponse.getConfidenceScore());
                response.setMessage("Identity routed to manual review");
                return response;
            }

        } catch (Exception e) {
            log.error("Error processing ingest request: {}", e.getMessage());
            return ApiGatewayResponse.error(e.getMessage());
        }
    }
}
