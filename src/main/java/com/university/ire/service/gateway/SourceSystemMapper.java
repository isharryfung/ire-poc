package com.university.ire.service.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.university.ire.dto.CanonicalIdentity;
import org.springframework.stereotype.Component;

@Component
public class SourceSystemMapper {

    public CanonicalIdentity toCanonical(String sourceSystem, String sourceRecordId, JsonNode payload) {
        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setSourceSystem(sourceSystem.toUpperCase());
        canonical.setSourceRecordId(sourceRecordId);

        canonical.setHkid(text(payload, "hkid"));
        canonical.setStaffId(text(payload, "staff_id", "staffId"));
        canonical.setStudentId(text(payload, "student_id", "studentId"));
        canonical.setBadgeId(text(payload, "badge_id", "badgeId"));
        canonical.setEmail(text(payload, "email"));
        canonical.setPhone(text(payload, "phone", "mobile"));

        String fullName = text(payload, "name", "full_name", "fullName");
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            canonical.setFirstName(parts[0]);
            canonical.setLastName(parts.length > 1 ? parts[1] : "");
        } else {
            canonical.setFirstName(text(payload, "first_name", "firstName"));
            canonical.setLastName(text(payload, "last_name", "lastName"));
        }
        return canonical;
    }

    private String text(JsonNode payload, String... fields) {
        for (String field : fields) {
            JsonNode node = payload.get(field);
            if (node != null && !node.isNull() && !node.asText().isBlank()) {
                return node.asText().trim();
            }
        }
        return null;
    }
}
