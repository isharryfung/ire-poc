package org.hkust.ire.web.controller;

import org.hkust.ire.db.persistence.service.gateway.ApiGatewayService;
import org.hkust.ire.dto.ApiGatewayRequest;
import org.hkust.ire.dto.ApiGatewayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the API Gateway ingestion endpoint.
 *
 * <p>Accepts identity payloads from all source systems and routes them
 * through the waterfall matching pipeline.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Controller
@RequestMapping("/api/v1")
public class ApiGatewayController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ApiGatewayService apiGatewayService;

    /**
     * Ingests an identity payload from any source system.
     *
     * <pre>
     * POST /api/v1/ingest
     * {
     *   "sourceSystem": "CRM",
     *   "sourceId": "CRM-001",
     *   "payload": { "email": "user@ust.hk", ... }
     * }
     * </pre>
     *
     * @param request the ingestion request
     * @return ApiGatewayResponse with resolution result
     */
    @PostMapping("/ingest")
    @ResponseBody
    public ResponseEntity<ApiGatewayResponse> ingest(@RequestBody ApiGatewayRequest request) {
        log.info("Received ingest request from sourceSystem={}", request.getSourceSystem());
        try {
            ApiGatewayResponse response = apiGatewayService.process(request);
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error processing ingest request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiGatewayResponse.error(e.getMessage()));
        }
    }
}
