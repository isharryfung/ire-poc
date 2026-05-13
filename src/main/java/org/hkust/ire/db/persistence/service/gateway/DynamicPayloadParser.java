package org.hkust.ire.db.persistence.service.gateway;

import org.hkust.ire.dto.CanonicalIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Parses dynamic JSON payloads from different source systems into a canonical form.
 *
 * <p>Each source system may use different field names. This parser normalizes
 * the fields into a {@link CanonicalIdentity} object.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class DynamicPayloadParser {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Parses a raw payload map into a CanonicalIdentity.
     *
     * @param sourceSystem the originating source system
     * @param payload      the raw payload map
     * @return CanonicalIdentity with normalized fields
     */
    public CanonicalIdentity parse(String sourceSystem, Map<String, Object> payload) {
        log.debug("Parsing payload from sourceSystem={}", sourceSystem);
        try {
            CanonicalIdentity canonical = new CanonicalIdentity();
            canonical.setSourceSystem(sourceSystem);

            canonical.setHkid(getString(payload, "hkid", "HKID"));
            canonical.setStaffId(getString(payload, "staffId", "staff_id", "STAFF_ID"));
            canonical.setStudentId(getString(payload, "studentId", "student_id", "STUDENT_ID"));
            canonical.setEmail(getString(payload, "email", "EMAIL", "emailAddress"));
            canonical.setFirstName(getString(payload, "firstName", "first_name", "givenName"));
            canonical.setLastName(getString(payload, "lastName", "last_name", "surname"));
            canonical.setPhone(getString(payload, "phone", "telephone", "mobile"));

            log.debug("Parsed canonical identity: email={}, staffId={}", canonical.getEmail(), canonical.getStaffId());
            return canonical;
        } catch (Exception e) {
            log.error("Error parsing payload from sourceSystem={}: {}", sourceSystem, e.getMessage());
            throw e;
        }
    }

    private String getString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
