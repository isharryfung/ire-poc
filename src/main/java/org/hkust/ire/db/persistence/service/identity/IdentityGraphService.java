package org.hkust.ire.db.persistence.service.identity;

import org.hkust.ire.db.persistence.domain.IdentityGraphDAO;
import org.hkust.ire.db.persistence.repository.IdentityGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages the identity relationship graph.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class IdentityGraphService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IdentityGraphRepository identityGraphRepository;

    /**
     * Creates a relationship edge between two identities.
     *
     * @param sourceGoldenId   source identity ID
     * @param targetGoldenId   target identity ID
     * @param relationshipType type of relationship
     * @param strength         relationship strength (0.0 to 1.0)
     * @return saved graph edge
     */
    @Transactional
    public IdentityGraphDAO addRelationship(String sourceGoldenId, String targetGoldenId,
                                             String relationshipType, Double strength) {
        log.info("Adding relationship: {} -> {} ({})", sourceGoldenId, targetGoldenId, relationshipType);
        try {
            IdentityGraphDAO edge = new IdentityGraphDAO(sourceGoldenId, targetGoldenId, relationshipType);
            edge.setRelationshipStrength(strength);
            edge.setStatus("ACTIVE");
            return identityGraphRepository.save(edge);
        } catch (Exception e) {
            log.error("Error adding relationship: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Returns all related identities for a given golden ID.
     *
     * @param goldenId the identity to search from
     * @return list of graph edges
     */
    public List<IdentityGraphDAO> getRelationships(String goldenId) {
        try {
            return identityGraphRepository.findBySourceGoldenIdOrTargetGoldenId(goldenId, goldenId);
        } catch (Exception e) {
            log.error("Error fetching relationships for goldenId={}: {}", goldenId, e.getMessage());
            throw e;
        }
    }
}
