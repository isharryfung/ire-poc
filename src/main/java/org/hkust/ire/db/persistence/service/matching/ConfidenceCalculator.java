package org.hkust.ire.db.persistence.service.matching;

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.dto.CanonicalIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Calculates composite confidence score between an incoming canonical identity
 * and an existing golden identity record.
 *
 * <p>Uses weighted field comparison: email (40%), name (30%), phone (15%), IDs (15%).</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class ConfidenceCalculator {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final double EMAIL_WEIGHT = 0.40;
    private static final double NAME_WEIGHT  = 0.30;
    private static final double PHONE_WEIGHT = 0.15;
    private static final double ID_WEIGHT    = 0.15;

    /**
     * Calculates a confidence score between a canonical identity and an existing record.
     *
     * @param incoming  the normalized incoming identity
     * @param existing  the existing golden identity
     * @return confidence score between 0.0 and 1.0
     */
    public double calculate(CanonicalIdentity incoming, IdentityDAO existing) {
        log.debug("Calculating confidence between incoming and goldenId={}", existing.getGoldenId());
        try {
            double score = 0.0;

            // Email match (40%)
            if (matches(incoming.getEmail(), existing.getEmail())) {
                score += EMAIL_WEIGHT;
            }

            // Name match (30%)
            double nameScore = 0.0;
            if (matches(incoming.getFirstName(), existing.getFirstName())) {
                nameScore += 0.5;
            }
            if (matches(incoming.getLastName(), existing.getLastName())) {
                nameScore += 0.5;
            }
            score += nameScore * NAME_WEIGHT;

            // Phone match (15%)
            if (matches(incoming.getPhone(), existing.getPhone())) {
                score += PHONE_WEIGHT;
            }

            // ID match (15%)
            double idScore = 0.0;
            if (matches(incoming.getHkid(), existing.getHkid())) {
                idScore = 1.0;
            } else if (matches(incoming.getStaffId(), existing.getStaffId())) {
                idScore = 1.0;
            } else if (matches(incoming.getStudentId(), existing.getStudentId())) {
                idScore = 1.0;
            }
            score += idScore * ID_WEIGHT;

            log.debug("Confidence score={} for goldenId={}", score, existing.getGoldenId());
            return Math.min(score, 1.0);

        } catch (Exception e) {
            log.error("Error calculating confidence: {}", e.getMessage());
            return 0.0;
        }
    }

    private boolean matches(String a, String b) {
        if (a == null || b == null || a.trim().isEmpty() || b.trim().isEmpty()) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
