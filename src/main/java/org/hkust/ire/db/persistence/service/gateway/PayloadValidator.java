package org.hkust.ire.db.persistence.service.gateway;

import org.hkust.ire.common.exception.InvalidPayloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Validates incoming API Gateway payloads before processing.
 *
 * <p>Checks required fields and enforces source system constraints.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class PayloadValidator {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Validates an incoming payload map.
     *
     * @param sourceSystem the originating source system
     * @param payload      the raw payload
     * @throws InvalidPayloadException if validation fails
     */
    public void validate(String sourceSystem, Map<String, Object> payload) {
        log.debug("Validating payload from sourceSystem={}", sourceSystem);
        try {
            if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
                throw new InvalidPayloadException("sourceSystem is required");
            }
            if (payload == null || payload.isEmpty()) {
                throw new InvalidPayloadException("payload must not be empty");
            }
            log.debug("Payload validation passed for sourceSystem={}", sourceSystem);
        } catch (InvalidPayloadException e) {
            log.error("Payload validation failed for sourceSystem={}: {}", sourceSystem, e.getMessage());
            throw e;
        }
    }
}
