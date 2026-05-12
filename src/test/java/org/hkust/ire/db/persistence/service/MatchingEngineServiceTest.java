package org.hkust.ire.db.persistence.service;

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.matching.MatchingEngineService;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.*;

/**
 * Tests for MatchingEngineService - verifies TIER-1/TIER-2/TIER-3 matching logic.
 *
 * @author ire-team
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MatchingEngineServiceTest {

    @Autowired
    private MatchingEngineService matchingEngineService;

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    public void testTier1MatchByHkid() {
        IdentityDAO identity = new IdentityDAO("GID-HKID-001", "hkid-user@ust.hk", "ACTIVE");
        identity.setHkid("A123456(7)");
        identityRepository.save(identity);

        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setHkid("A123456(7)");
        canonical.setSourceSystem("CRM");

        IdentityMatchResponse response = matchingEngineService.match(canonical);

        assertTrue(response.isMatched());
        assertEquals("TIER_1", response.getMatchTier());
        assertEquals("GID-HKID-001", response.getGoldenId());
    }

    @Test
    public void testTier1MatchByStaffId() {
        IdentityDAO identity = new IdentityDAO("GID-STAFF-001", "staff@ust.hk", "ACTIVE");
        identity.setStaffId("S100001");
        identityRepository.save(identity);

        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setStaffId("S100001");
        canonical.setSourceSystem("ADMS");

        IdentityMatchResponse response = matchingEngineService.match(canonical);

        assertTrue(response.isMatched());
        assertEquals("TIER_1", response.getMatchTier());
        assertEquals("GID-STAFF-001", response.getGoldenId());
    }

    @Test
    public void testNoMatchReturnsUnmatched() {
        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setEmail("nobody@unknown.com");
        canonical.setFirstName("Unknown");
        canonical.setSourceSystem("THIRD_PARTY");

        IdentityMatchResponse response = matchingEngineService.match(canonical);

        assertNotNull(response);
        assertFalse(response.isMatched());
    }

    @Test
    public void testTier2MatchByNameAndEmail() {
        IdentityDAO identity = new IdentityDAO("GID-T2-001", "chan@ust.hk", "ACTIVE");
        identity.setFirstName("Chan");
        identity.setLastName("Tai Man");
        identity.setPhone("98765432");
        identityRepository.save(identity);

        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setEmail("chan@ust.hk");
        canonical.setFirstName("Chan");
        canonical.setLastName("Tai Man");
        canonical.setSourceSystem("EVENT_SYSTEM");

        IdentityMatchResponse response = matchingEngineService.match(canonical);

        assertNotNull(response);
        assertTrue(response.isMatched());
    }
}
