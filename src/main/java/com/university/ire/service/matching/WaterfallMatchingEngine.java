package com.university.ire.service.matching;

import com.university.ire.dto.CanonicalIdentity;
import com.university.ire.entity.Identity;
import com.university.ire.repository.IdentityRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

@Component
public class WaterfallMatchingEngine {

    public enum Tier { TIER_1, TIER_2, TIER_3 }

    public record MatchResult(Tier tier, Optional<Identity> identity, double baseScore) {}

    private final IdentityRepository identityRepository;

    public WaterfallMatchingEngine(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    public MatchResult match(CanonicalIdentity input) {
        Optional<Identity> deterministic = tier1(input);
        if (deterministic.isPresent()) {
            return new MatchResult(Tier.TIER_1, deterministic, 1.0);
        }

        Optional<ScoredIdentity> probabilistic = tier2(input);
        if (probabilistic.isPresent()) {
            ScoredIdentity result = probabilistic.get();
            return new MatchResult(Tier.TIER_2, Optional.of(result.identity()), result.score());
        }

        return new MatchResult(Tier.TIER_3, Optional.empty(), 0.0);
    }

    private Optional<Identity> tier1(CanonicalIdentity input) {
        if (input.getHkid() != null && !input.getHkid().isBlank()) {
            Optional<Identity> byHkid = identityRepository.findByHkid(input.getHkid());
            if (byHkid.isPresent()) return byHkid;
        }
        if (input.getStudentId() != null && !input.getStudentId().isBlank()) {
            Optional<Identity> byStudentId = identityRepository.findByStudentId(input.getStudentId());
            if (byStudentId.isPresent()) return byStudentId;
        }
        if (input.getStaffId() != null && !input.getStaffId().isBlank()) {
            Optional<Identity> byStaff = identityRepository.findByStaffId(input.getStaffId());
            if (byStaff.isPresent()) return byStaff;
        }
        return Optional.empty();
    }

    private Optional<ScoredIdentity> tier2(CanonicalIdentity input) {
        if (input.getEmail() == null || input.getEmail().isBlank()) {
            return Optional.empty();
        }

        List<Identity> candidates = identityRepository.findAllByEmailIgnoreCase(input.getEmail());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String incomingName = input.fullName().toLowerCase();
        LevenshteinDistance distance = LevenshteinDistance.getDefaultInstance();

        return candidates.stream()
                .filter(c -> c.getEmail() != null && c.getEmail().equalsIgnoreCase(input.getEmail()))
                .map(c -> {
                    String candidateName = ((c.getFirstName() == null ? "" : c.getFirstName()) + " "
                            + (c.getLastName() == null ? "" : c.getLastName())).trim().toLowerCase();
                    int maxLen = Math.max(incomingName.length(), candidateName.length());
                    if (maxLen == 0) {
                        return new ScoredIdentity(c, 0.9);
                    }
                    Integer rawDistance = distance.apply(incomingName, candidateName);
                    double similarity = 1.0 - ((double) rawDistance / maxLen);
                    return new ScoredIdentity(c, Math.max(0.0, Math.min(0.99, 0.9 + (similarity * 0.09))));
                })
                .max(Comparator.comparingDouble(ScoredIdentity::score))
                .filter(scored -> scored.score() >= 0.90);
    }

    private record ScoredIdentity(Identity identity, double score) {}
}
