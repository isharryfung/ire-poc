package org.hkust.ire.db.persistence.service.gateway;

import org.hkust.ire.common.constant.SourceSystemConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Maps source system payloads to canonical form by detecting source system type.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class SourceSystemMapper {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Detects and normalizes the source system identifier from an incoming request.
     *
     * @param rawSourceSystem the raw source system value from the request
     * @param payload         the payload map (used for auto-detection if needed)
     * @return normalized source system constant
     */
    public String detectSourceSystem(String rawSourceSystem, Map<String, Object> payload) {
        log.debug("Detecting source system from rawValue={}", rawSourceSystem);
        try {
            if (rawSourceSystem == null) {
                return detectFromPayload(payload);
            }
            String upper = rawSourceSystem.toUpperCase().trim();
            if (upper.contains("CRM")) {
                return SourceSystemConstant.CRM;
            } else if (upper.contains("ADMS") || upper.contains("ADMISSION")) {
                return SourceSystemConstant.ADMS;
            } else if (upper.contains("ATTEND")) {
                return SourceSystemConstant.ATTENDANCE;
            } else if (upper.contains("EVENT")) {
                return SourceSystemConstant.EVENT_SYSTEM;
            } else if (upper.contains("IAM") || upper.contains("MIDPOINT")) {
                return SourceSystemConstant.IAM;
            } else {
                return SourceSystemConstant.THIRD_PARTY;
            }
        } catch (Exception e) {
            log.error("Error detecting source system: {}", e.getMessage());
            return SourceSystemConstant.THIRD_PARTY;
        }
    }

    private String detectFromPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return SourceSystemConstant.THIRD_PARTY;
        }
        if (payload.containsKey("crmId") || payload.containsKey("crm_id")) {
            return SourceSystemConstant.CRM;
        }
        if (payload.containsKey("studentId") || payload.containsKey("student_id")) {
            return SourceSystemConstant.ADMS;
        }
        if (payload.containsKey("attendanceDate") || payload.containsKey("attendance_date")) {
            return SourceSystemConstant.ATTENDANCE;
        }
        return SourceSystemConstant.THIRD_PARTY;
    }
}
