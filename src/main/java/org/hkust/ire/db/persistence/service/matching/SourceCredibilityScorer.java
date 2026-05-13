package org.hkust.ire.db.persistence.service.matching;

import org.hkust.ire.common.constant.SourceSystemConstant;
import org.hkust.ire.db.persistence.repository.SourceCredibilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Scores the credibility of source systems for use in composite confidence calculation.
 *
 * <p>Credibility weights: CRM=1.0, ADMS/Attendance=0.9, 3rd-party=0.8</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class SourceCredibilityScorer {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SourceCredibilityRepository sourceCredibilityRepository;

    /**
     * Returns the credibility score for a given source system.
     * Falls back to configured values if DB record not found.
     *
     * @param sourceSystem the source system name
     * @return credibility weight (0.0 to 1.0)
     */
    public double score(String sourceSystem) {
        log.debug("Scoring credibility for sourceSystem={}", sourceSystem);
        try {
            return sourceCredibilityRepository.findBySourceSystem(sourceSystem)
                    .map(dao -> dao.getCredibilityScore())
                    .orElse(getDefaultCredibility(sourceSystem));
        } catch (Exception e) {
            log.error("Error fetching credibility for sourceSystem={}: {}", sourceSystem, e.getMessage());
            return getDefaultCredibility(sourceSystem);
        }
    }

    private double getDefaultCredibility(String sourceSystem) {
        if (sourceSystem == null) {
            return SourceSystemConstant.CREDIBILITY_THIRD_PARTY;
        }
        String upper = sourceSystem.toUpperCase();
        if (upper.contains("CRM")) {
            return SourceSystemConstant.CREDIBILITY_CRM;
        } else if (upper.contains("ADMS") || upper.contains("ATTEND")) {
            return SourceSystemConstant.CREDIBILITY_ADMS;
        } else {
            return SourceSystemConstant.CREDIBILITY_THIRD_PARTY;
        }
    }
}
