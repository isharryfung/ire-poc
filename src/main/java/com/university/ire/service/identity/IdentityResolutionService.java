package com.university.ire.service.identity;

import com.university.ire.dto.ApiGatewayResponse;
import com.university.ire.dto.CanonicalIdentity;
import com.university.ire.entity.Identity;
import com.university.ire.entity.IdentityLink;
import com.university.ire.entity.RelationshipType;
import com.university.ire.repository.IdentityLinkRepository;
import com.university.ire.repository.IdentityRepository;
import com.university.ire.service.matching.MatchingEngineService;
import com.university.ire.service.review.ManualReviewService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityResolutionService {

    private final MatchingEngineService matchingEngineService;
    private final IdentityRepository identityRepository;
    private final IdentityLinkRepository identityLinkRepository;
    private final IdentityMergeService identityMergeService;
    private final IdentityGraphService identityGraphService;
    private final ManualReviewService manualReviewService;

    public IdentityResolutionService(
            MatchingEngineService matchingEngineService,
            IdentityRepository identityRepository,
            IdentityLinkRepository identityLinkRepository,
            IdentityMergeService identityMergeService,
            IdentityGraphService identityGraphService,
            ManualReviewService manualReviewService) {
        this.matchingEngineService = matchingEngineService;
        this.identityRepository = identityRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.identityMergeService = identityMergeService;
        this.identityGraphService = identityGraphService;
        this.manualReviewService = manualReviewService;
    }

    @Transactional
    public ApiGatewayResponse resolve(CanonicalIdentity canonicalIdentity) {
        MatchingEngineService.MatchingOutcome outcome = matchingEngineService.resolve(canonicalIdentity);

        if ("AUTO_MERGE".equals(outcome.action()) && outcome.matchedIdentity() != null) {
            Identity merged = identityMergeService.mergeInto(outcome.matchedIdentity(), canonicalIdentity);
            identityRepository.save(merged);
            createLink(merged, canonicalIdentity);
            return new ApiGatewayResponse(
                    String.valueOf(merged.getId()),
                    outcome.action(),
                    outcome.tier().name(),
                    outcome.confidence(),
                    null,
                    "Identity auto-merged");
        }

        if ("MANUAL_REVIEW".equals(outcome.action())) {
            String candidateId = outcome.matchedIdentity() == null ? null : String.valueOf(outcome.matchedIdentity().getId());
            var review = manualReviewService.create(canonicalIdentity.getSourceRecordId(), candidateId, outcome.confidence(), "Confidence requires reviewer decision");
            return new ApiGatewayResponse(
                    candidateId,
                    outcome.action(),
                    outcome.tier().name(),
                    outcome.confidence(),
                    String.valueOf(review.getId()),
                    "Routed to manual review queue");
        }

        Identity created = createIdentity(canonicalIdentity);
        createLink(created, canonicalIdentity);
        if (outcome.matchedIdentity() != null) {
            identityGraphService.link(created, outcome.matchedIdentity(), RelationshipType.VARIANT, outcome.confidence());
        }
        return new ApiGatewayResponse(
                String.valueOf(created.getId()),
                outcome.action(),
                outcome.tier().name(),
                outcome.confidence(),
                null,
                "New identity created");
    }

    private Identity createIdentity(CanonicalIdentity canonicalIdentity) {
        Identity identity = new Identity();
        identity.setIdentityKey(UUID.randomUUID().toString());
        identity.setHkid(canonicalIdentity.getHkid());
        identity.setStaffId(canonicalIdentity.getStaffId());
        identity.setStudentId(canonicalIdentity.getStudentId());
        identity.setBadgeId(canonicalIdentity.getBadgeId());
        identity.setEmail(canonicalIdentity.getEmail());
        identity.setPhone(canonicalIdentity.getPhone());
        identity.setFirstName(canonicalIdentity.getFirstName());
        identity.setLastName(canonicalIdentity.getLastName());
        return identityRepository.save(identity);
    }

    private void createLink(Identity identity, CanonicalIdentity canonicalIdentity) {
        IdentityLink link = new IdentityLink();
        link.setIdentity(identity);
        link.setSourceSystem(canonicalIdentity.getSourceSystem());
        link.setSourceRecordId(canonicalIdentity.getSourceRecordId());
        identityLinkRepository.save(link);
    }
}
