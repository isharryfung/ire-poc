package org.hkust.ire.db.persistence.service;

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.identity.IdentityResolutionService;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Integration tests for IdentityResolutionService.
 *
 * @author ire-team
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class IdentityResolutionServiceTest {

    @Autowired
    private IdentityResolutionService identityResolutionService;

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    public void testResolveNewIdentityCreatesGoldenRecord() {
        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setEmail("newuser@ust.hk");
        canonical.setFirstName("New");
        canonical.setLastName("User");
        canonical.setSourceSystem("CRM");
        canonical.setSourceId("CRM-NEW-001");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response);
        assertNotNull(response.getGoldenId());
        assertTrue(response.isMatched());

        Optional<IdentityDAO> saved = identityRepository.findByGoldenId(response.getGoldenId());
        assertTrue(saved.isPresent());
        assertEquals("newuser@ust.hk", saved.get().getEmail());
    }

    @Test
    public void testResolveTier1MatchByEmail() {
        // Seed an existing identity
        IdentityDAO existing = new IdentityDAO("GID-TEST-001", "existing@ust.hk", "ACTIVE");
        existing.setFirstName("Existing");
        existing.setLastName("User");
        identityRepository.save(existing);

        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setEmail("existing@ust.hk");
        canonical.setSourceSystem("ADMS");
        canonical.setSourceId("ADMS-001");

        IdentityMatchResponse response = identityResolutionService.resolve(canonical);

        assertNotNull(response);
        assertTrue(response.isMatched());
        assertEquals("GID-TEST-001", response.getGoldenId());
        assertEquals("TIER_1", response.getMatchTier());
    }
}
