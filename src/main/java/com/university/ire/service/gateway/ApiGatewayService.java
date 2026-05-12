package com.university.ire.service.gateway;

import com.university.ire.dto.ApiGatewayRequest;
import com.university.ire.dto.ApiGatewayResponse;
import com.university.ire.dto.CanonicalIdentity;
import com.university.ire.service.identity.IdentityResolutionService;
import com.university.ire.service.monitoring.MetricsService;
import org.springframework.stereotype.Service;

@Service
public class ApiGatewayService {

    private final DynamicPayloadParser dynamicPayloadParser;
    private final PayloadValidator payloadValidator;
    private final IdentityResolutionService identityResolutionService;
    private final MetricsService metricsService;

    public ApiGatewayService(
            DynamicPayloadParser dynamicPayloadParser,
            PayloadValidator payloadValidator,
            IdentityResolutionService identityResolutionService,
            MetricsService metricsService) {
        this.dynamicPayloadParser = dynamicPayloadParser;
        this.payloadValidator = payloadValidator;
        this.identityResolutionService = identityResolutionService;
        this.metricsService = metricsService;
    }

    public ApiGatewayResponse ingest(ApiGatewayRequest request) {
        long start = System.nanoTime();
        CanonicalIdentity canonicalIdentity = dynamicPayloadParser.parse(request);
        payloadValidator.validate(canonicalIdentity);
        ApiGatewayResponse response = identityResolutionService.resolve(canonicalIdentity);

        metricsService.incrementTier(response.tier());
        metricsService.recordConfidence(response.confidence());
        metricsService.recordLatency("api.ingest", System.nanoTime() - start);
        return response;
    }
}
