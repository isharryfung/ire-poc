package org.hkust.ire.integration;

import org.hkust.ire.db.persistence.service.gateway.ApiGatewayService;
import org.hkust.ire.db.persistence.service.identity.IdentityResolutionService;
import org.hkust.ire.dto.ApiGatewayRequest;
import org.hkust.ire.dto.ApiGatewayResponse;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full pipeline integration test: API Gateway → Matching → Resolution.
 *
 * @author ire-team
 * @since 1.0.0
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FullPipelineIntegrationTest {

    @Autowired
    private ApiGatewayService apiGatewayService;

    @Autowired
    private IdentityResolutionService identityResolutionService;

    @Test
    public void testCrmPayloadFullPipeline() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "pipeline.user@ust.hk");
        payload.put("firstName", "Pipeline");
        payload.put("lastName", "User");
        payload.put("staffId", "S999001");

        ApiGatewayRequest request = new ApiGatewayRequest("CRM", "CRM-PIPELINE-001", payload);
        request.setRequestId("INT-001");

        ApiGatewayResponse response = apiGatewayService.process(request);

        assertNotNull(response);
        assertTrue(response.isSuccess(), "Expected success but got: " + response.getMessage());
        assertNotNull(response.getGoldenId());
        assertNotNull(response.getMatchTier());
    }

    @Test
    public void testDuplicateIngestResolvesSameGoldenId() {
        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setEmail("duplicate.user@ust.hk");
        canonical.setStaffId("S888001");
        canonical.setFirstName("Dup");
        canonical.setLastName("User");
        canonical.setSourceSystem("CRM");
        canonical.setSourceId("CRM-DUP-001");

        IdentityMatchResponse first = identityResolutionService.resolve(canonical);
        assertNotNull(first.getGoldenId());

        // Second ingest with same staffId - should match TIER-1
        CanonicalIdentity canonical2 = new CanonicalIdentity();
        canonical2.setEmail("duplicate.user.v2@ust.hk");
        canonical2.setStaffId("S888001");
        canonical2.setSourceSystem("ADMS");
        canonical2.setSourceId("ADMS-DUP-001");

        IdentityMatchResponse second = identityResolutionService.resolve(canonical2);
        assertNotNull(second.getGoldenId());
        assertEquals("Same staffId should resolve to same golden record",
                first.getGoldenId(), second.getGoldenId());
        assertEquals("TIER_1", second.getMatchTier());
    }
}
