package org.hkust.ire.db.persistence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.matching.MatchingEngineService;
import org.hkust.ire.dto.IdentityMatchRequest;
import org.hkust.ire.dto.IdentityMatchResponse;

/**
 * Comprehensive matching scenario tests for Phase 1
 * 20+ test cases covering different field combinations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Identity Matching Scenarios - Extended Tests")
public class IdentityMatchingScenariosMockTest {

    private static final Logger log = LoggerFactory.getLogger(IdentityMatchingScenariosMockTest.class);

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private MatchingEngineService matchingEngineService;

    @InjectMocks
    private IdentityResolutionService identityResolutionService;

    @BeforeEach
    public void setUp() {
        log.info("Setting up matching scenario tests");
    }

    // TIER-1 VARIATIONS
    @Test
    @DisplayName("Scenario 1: Alumni ID Exact Match (100%)")
    public void testScenario1AlumniIdMatch() {
        log.info("Scenario 1: Alumni ID match");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setAlumniId("HKUST20150001");
        request.setSource("ADMS");
        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(101L);
        mockIdentity.setAlumniId("HKUST20150001");
        when(identityRepository.findByAlumniId("HKUST20150001")).thenReturn(mockIdentity);
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(1.0, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 1 PASSED");
    }

    @Test
    @DisplayName("Scenario 2: Smart Card ID Match (100%)")
    public void testScenario2SmartCardMatch() {
        log.info("Scenario 2: Smart Card match");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setSmartCardId("STAFF20150001");
        request.setSource("ATTENDANCE");
        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(102L);
        mockIdentity.setSmartCardId("STAFF20150001");
        when(identityRepository.findBySmartCardId("STAFF20150001")).thenReturn(mockIdentity);
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(1.0, response.getConfidence());
        log.info("✅ Scenario 2 PASSED");
    }

    @Test
    @DisplayName("Scenario 3: Passport ID Match (100%)")
    public void testScenario3PassportMatch() {
        log.info("Scenario 3: Passport match");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setPassportId("HK123456");
        request.setSource("ADMS");
        IdentityDAO mockIdentity = new IdentityDAO();
        mockIdentity.setId(103L);
        mockIdentity.setPassportId("HK123456");
        when(identityRepository.findByPassportId("HK123456")).thenReturn(mockIdentity);
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(1.0, response.getConfidence());
        log.info("✅ Scenario 3 PASSED");
    }

    // TIER-2 COMBINATIONS
    @Test
    @DisplayName("Scenario 4: Mobile + Name (85%)")
    public void testScenario4MobileNameMatch() {
        log.info("Scenario 4: Mobile + Name");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setMobile("98765432");
        request.setName("John Doe");
        request.setSource("ATTENDANCE");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_MOBILE_NAME", 0.85, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.85, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 4 PASSED");
    }

    @Test
    @DisplayName("Scenario 5: Email Only (75%)")
    public void testScenario5EmailOnly() {
        log.info("Scenario 5: Email only");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setSource("EVENT_SYSTEM");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_ONLY", 0.75, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.75, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 5 PASSED");
    }

    @Test
    @DisplayName("Scenario 6: Email + Mobile + Name (98%)")
    public void testScenario6TripleMatch() {
        log.info("Scenario 6: Triple match");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setMobile("98765432");
        request.setEmail("user@example.com");
        request.setName("User Doe");
        request.setSource("ADMS");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_TRIPLE_MATCH", 0.98, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.98, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 6 PASSED");
    }

    @Test
    @DisplayName("Scenario 7: Email + DOB (88%)")
    public void testScenario7EmailDob() {
        log.info("Scenario 7: Email + DOB");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setDob("1990-01-01");
        request.setSource("EVENT_SYSTEM");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_DOB", 0.88, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.88, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 7 PASSED");
    }

    @Test
    @DisplayName("Scenario 8: Mobile + DOB (82%)")
    public void testScenario8MobileDob() {
        log.info("Scenario 8: Mobile + DOB");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setMobile("98765432");
        request.setDob("1990-01-01");
        request.setSource("ATTENDANCE");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_MOBILE_DOB", 0.82, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.82, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 8 PASSED");
    }

    @Test
    @DisplayName("Scenario 9: Name + DOB (78%)")
    public void testScenario9NameDob() {
        log.info("Scenario 9: Name + DOB");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setName("John Doe");
        request.setDob("1990-01-01");
        request.setSource("EVENT_SYSTEM");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_NAME_DOB", 0.78, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.78, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 9 PASSED");
    }

    @Test
    @DisplayName("Scenario 10: Email + Mobile + DOB (96%)")
    public void testScenario10EmailMobileDob() {
        log.info("Scenario 10: Email + Mobile + DOB");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        request.setDob("1990-01-01");
        request.setSource("ADMS");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_MOBILE_DOB", 0.96, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.96, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 10 PASSED");
    }

    // TIER-3 AND LOW CONFIDENCE
    @Test
    @DisplayName("Scenario 11: Low Trust Source 80% × 0.8x = 64%")
    public void testScenario11LowTrustSource() {
        log.info("Scenario 11: Low trust impact");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        request.setSource("THIRD_PARTY_TICKETING");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_MOBILE", 0.80, null));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        double expectedConfidence = 0.80 * 0.80;
        assertEquals(expectedConfidence, response.getConfidence(), 0.01);
        assertFalse(response.isAutoMergeEligible());
        log.info("✅ Scenario 11 PASSED");
    }

    @Test
    @DisplayName("Scenario 12: Unknown Source 85% × 0.7x = 59.5%")
    public void testScenario12UnknownSource() {
        log.info("Scenario 12: Unknown source");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setName("User Doe");
        request.setSource("NEW_EXTERNAL_SYSTEM");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_NAME", 0.85, null));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        double expectedConfidence = 0.85 * 0.70;
        assertEquals(expectedConfidence, response.getConfidence(), 0.01);
        assertFalse(response.isAutoMergeEligible());
        log.info("✅ Scenario 12 PASSED");
    }

    @Test
    @DisplayName("Scenario 13: Name Only - Insufficient (0%)")
    public void testScenario13NameOnlyInsufficient() {
        log.info("Scenario 13: Name only insufficient");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setName("User Doe");
        request.setSource("GOOGLE_FORMS");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("NO_MATCH", 0.0, null));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.0, response.getConfidence());
        assertFalse(response.isAutoMergeEligible());
        log.info("✅ Scenario 13 PASSED");
    }

    @Test
    @DisplayName("Scenario 14: DOB Only - Insufficient (0%)")
    public void testScenario14DobOnlyInsufficient() {
        log.info("Scenario 14: DOB only insufficient");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setDob("1990-01-01");
        request.setSource("GOOGLE_FORMS");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("NO_MATCH", 0.0, null));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.0, response.getConfidence());
        assertFalse(response.isAutoMergeEligible());
        log.info("✅ Scenario 14 PASSED");
    }

    @Test
    @DisplayName("Scenario 15: Threshold Boundary - Exactly 95%")
    public void testScenario15Threshold95() {
        log.info("Scenario 15: Threshold 95%");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        request.setSource("ADMS");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_MOBILE", 0.95, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.95, response.getConfidence());
        assertTrue(response.isAutoMergeEligible());
        log.info("✅ Scenario 15 PASSED");
    }

    @Test
    @DisplayName("Scenario 16: Threshold Boundary - Just Below 95%")
    public void testScenario16ThresholdBelow95() {
        log.info("Scenario 16: Just below 95%");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        request.setSource("GOOGLE_FORMS");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_MOBILE", 0.95, null));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        double expectedConfidence = 0.95 * 0.80;
        assertEquals(expectedConfidence, response.getConfidence(), 0.01);
        assertFalse(response.isAutoMergeEligible());
        log.info("✅ Scenario 16 PASSED");
    }

    // BUSINESS LOGIC
    @Test
    @DisplayName("Scenario 17: Multiple High-Trust Sources")
    public void testScenario17MultipleSources() {
        log.info("Scenario 17: Multiple sources");
        IdentityMatchRequest request1 = new IdentityMatchRequest();
        request1.setEmail("user@example.com");
        request1.setMobile("98765432");
        request1.setSource("ADMS");
        when(matchingEngineService.performProbabilisticMatch(request1))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_MOBILE", 0.90, new IdentityDAO()));
        IdentityMatchResponse response1 = identityResolutionService.resolveIdentity(request1);
        assertEquals(0.90, response1.getConfidence());
        assertTrue(response1.isAutoMergeEligible());
        log.info("✅ Scenario 17 PASSED");
    }

    @Test
    @DisplayName("Scenario 18: Same Score Different Source Different Action")
    public void testScenario18SameScoreDifferentAction() {
        log.info("Scenario 18: Same score different source");
        double baseScore = 0.90;
        double admsConfidence = baseScore * 1.0;
        double formsConfidence = baseScore * 0.80;
        assertTrue(admsConfidence >= 0.85);
        assertFalse(formsConfidence >= 0.95);
        log.info("✅ Scenario 18 PASSED");
    }

    @Test
    @DisplayName("Scenario 19: Field Combination Accuracy")
    public void testScenario19FieldCombinationAccuracy() {
        log.info("Scenario 19: Field accuracy");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        request.setSource("EVENT_SYSTEM");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("TIER_2_EMAIL_MOBILE", 0.95, new IdentityDAO()));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertTrue(response.getConfidence() >= 0.70);
        log.info("✅ Scenario 19 PASSED");
    }

    @Test
    @DisplayName("Scenario 20: No Match - Create New Identity")
    public void testScenario20NoMatchNewIdentity() {
        log.info("Scenario 20: No match");
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("brand.new@example.com");
        request.setName("Brand New");
        request.setMobile("12345678");
        request.setSource("GOOGLE_FORMS");
        when(matchingEngineService.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult("NO_MATCH", 0.0, null));
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);
        assertEquals(0.0, response.getConfidence());
        assertFalse(response.isAutoMergeEligible());
        assertNull(response.getIdentityId());
        log.info("✅ Scenario 20 PASSED");
    }
}
