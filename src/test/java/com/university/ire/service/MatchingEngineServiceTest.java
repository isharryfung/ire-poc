package com.university.ire.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.ire.dto.CanonicalIdentity;
import com.university.ire.entity.Identity;
import com.university.ire.repository.IdentityRepository;
import com.university.ire.repository.SourceCredibilityRepository;
import com.university.ire.service.matching.ConfidenceCalculator;
import com.university.ire.service.matching.MatchingEngineService;
import com.university.ire.service.matching.SourceCredibilityScorer;
import com.university.ire.service.matching.WaterfallMatchingEngine;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchingEngineServiceTest {

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private SourceCredibilityRepository sourceCredibilityRepository;

    private MatchingEngineService matchingEngineService;

    @BeforeEach
    void setUp() {
        WaterfallMatchingEngine waterfallMatchingEngine = new WaterfallMatchingEngine(identityRepository);
        SourceCredibilityScorer sourceCredibilityScorer = new SourceCredibilityScorer(sourceCredibilityRepository);
        matchingEngineService = new MatchingEngineService(waterfallMatchingEngine, sourceCredibilityScorer, new ConfidenceCalculator());
    }

    @Test
    void shouldReturnAutoMergeForTier1DeterministicMatch() {
        CanonicalIdentity input = new CanonicalIdentity();
        input.setSourceSystem("CRM");
        input.setHkid("A123456(7)");

        Identity existing = new Identity();
        existing.setIdentityKey("id-1");
        when(identityRepository.findByHkid("A123456(7)")).thenReturn(Optional.of(existing));

        MatchingEngineService.MatchingOutcome outcome = matchingEngineService.resolve(input);

        assertThat(outcome.action()).isEqualTo("AUTO_MERGE");
        assertThat(outcome.tier().name()).isEqualTo("TIER_1");
        assertThat(outcome.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldRouteToManualReviewForTier2LowerCredibility() {
        CanonicalIdentity input = new CanonicalIdentity();
        input.setSourceSystem("3RD_PARTY");
        input.setEmail("amy.chan@hkust.edu.hk");
        input.setFirstName("Amy");
        input.setLastName("Chan");

        Identity existing = new Identity();
        existing.setIdentityKey("id-2");
        existing.setEmail("amy.chan@hkust.edu.hk");
        existing.setFirstName("Amy");
        existing.setLastName("Chan");

        when(identityRepository.findAllByEmailIgnoreCase("amy.chan@hkust.edu.hk")).thenReturn(List.of(existing));

        MatchingEngineService.MatchingOutcome outcome = matchingEngineService.resolve(input);

        assertThat(outcome.tier().name()).isEqualTo("TIER_2");
        assertThat(outcome.action()).isEqualTo("MANUAL_REVIEW");
        assertThat(outcome.confidence()).isBetween(0.50, 0.95);
    }
}
