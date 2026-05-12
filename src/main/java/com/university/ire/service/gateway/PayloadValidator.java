package com.university.ire.service.gateway;

import com.university.ire.dto.CanonicalIdentity;
import com.university.ire.exception.InvalidPayloadException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PayloadValidator {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public void validate(CanonicalIdentity canonicalIdentity) {
        if ((canonicalIdentity.getStudentId() == null || canonicalIdentity.getStudentId().isBlank())
                && (canonicalIdentity.getStaffId() == null || canonicalIdentity.getStaffId().isBlank())
                && (canonicalIdentity.getEmail() == null || canonicalIdentity.getEmail().isBlank())
                && (canonicalIdentity.getHkid() == null || canonicalIdentity.getHkid().isBlank())) {
            throw new InvalidPayloadException("At least one identifier is required (hkid, student_id, staff_id, email)");
        }

        String email = canonicalIdentity.getEmail();
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidPayloadException("Invalid email format");
        }
    }
}
