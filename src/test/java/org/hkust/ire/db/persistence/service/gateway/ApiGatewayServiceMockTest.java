package org.hkust.ire.db.persistence.service.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hkust.ire.common.constant.SourceSystemConstant;
import org.hkust.ire.db.persistence.service.identity.IdentityResolutionService;
import org.hkust.ire.dto.ApiGatewayRequest;
import org.hkust.ire.dto.ApiGatewayResponse;
import org.hkust.ire.dto.CanonicalIdentity;
import org.hkust.ire.dto.IdentityMatchResponse;

/**
 * Mock-based unit tests for API Gateway Service
 *
 * Tests flexible payload parsing from multiple source systems.
 *
 * @author isharryfung
 * @since 2026-05-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API Gateway Service - Mock Tests")
public class ApiGatewayServiceMockTest {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayServiceMockTest.class);

    @Mock
    private PayloadValidator payloadValidator;

    @Mock
    private DynamicPayloadParser dynamicPayloadParser;

    @Mock
    private SourceSystemMapper sourceSystemMapper;

    @Mock
    private IdentityResolutionService identityResolutionService;

    @InjectMocks
    private ApiGatewayService apiGatewayService;

    @BeforeEach
    public void setUp() {
        log.info("Setting up API Gateway Service tests");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ApiGatewayRequest buildRequest(String source, Map<String, Object> payload) {
        ApiGatewayRequest r = new ApiGatewayRequest();
        r.setSourceSystem(source);
        r.setSourceId("SRC-001");
        r.setPayload(payload);
        return r;
    }

    private IdentityMatchResponse matchedResponse(String goldenId, String tier, double score) {
        IdentityMatchResponse r = new IdentityMatchResponse();
        r.setMatched(true);
        r.setGoldenId(goldenId);
        r.setMatchTier(tier);
        r.setConfidenceScore(score);
        r.setStatus("MATCHED");
        return r;
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * Test: Parse Event System Payload (Email + Name)
     */
    @Test
    @DisplayName("Parse Event System Payload (Email + Name)")
    public void testParseEventSystemPayload() {
        log.info("Test: Parse Event System payload");

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "john@example.com");
        payload.put("firstName", "John");
        payload.put("lastName", "Doe");

        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setEmail("john@example.com");
        canonical.setFirstName("John");
        canonical.setLastName("Doe");
        canonical.setSourceSystem(SourceSystemConstant.EVENT_SYSTEM);

        when(sourceSystemMapper.detectSourceSystem(eq("EVENT_SYSTEM"), any()))
            .thenReturn(SourceSystemConstant.EVENT_SYSTEM);
        when(dynamicPayloadParser.parse(eq(SourceSystemConstant.EVENT_SYSTEM), any()))
            .thenReturn(canonical);
        when(identityResolutionService.resolve(any(CanonicalIdentity.class)))
            .thenReturn(matchedResponse("GOLDEN-101", "TIER_1", 1.0));

        ApiGatewayResponse response = apiGatewayService.process(buildRequest("EVENT_SYSTEM", payload));

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("GOLDEN-101", response.getGoldenId());

        log.info("Event System payload test PASSED");
    }

    /**
     * Test: Parse Attendance System Payload (Staff ID)
     */
    @Test
    @DisplayName("Parse Attendance System Payload (Staff ID)")
    public void testParseAttendancePayload() {
        log.info("Test: Parse Attendance payload");

        Map<String, Object> payload = new HashMap<>();
        payload.put("staff_id", "STAFF20150001");

        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setStaffId("STAFF20150001");
        canonical.setSourceSystem(SourceSystemConstant.ATTENDANCE);

        when(sourceSystemMapper.detectSourceSystem(eq("ATTENDANCE"), any()))
            .thenReturn(SourceSystemConstant.ATTENDANCE);
        when(dynamicPayloadParser.parse(eq(SourceSystemConstant.ATTENDANCE), any()))
            .thenReturn(canonical);
        when(identityResolutionService.resolve(any(CanonicalIdentity.class)))
            .thenReturn(matchedResponse("GOLDEN-102", "TIER_1", 1.0));

        ApiGatewayResponse response = apiGatewayService.process(buildRequest("ATTENDANCE", payload));

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("GOLDEN-102", response.getGoldenId());

        log.info("Attendance payload test PASSED");
    }

    /**
     * Test: Parse 3rd-Party Form Payload (Phone + Name)
     */
    @Test
    @DisplayName("Parse 3rd-Party Form Payload (Phone + Name)")
    public void testParse3rdPartyFormPayload() {
        log.info("Test: Parse 3rd-party form payload");

        Map<String, Object> payload = new HashMap<>();
        payload.put("mobile", "98765432");
        payload.put("firstName", "User");
        payload.put("lastName", "Doe");

        CanonicalIdentity canonical = new CanonicalIdentity();
        canonical.setPhone("98765432");
        canonical.setFirstName("User");
        canonical.setLastName("Doe");
        canonical.setSourceSystem(SourceSystemConstant.THIRD_PARTY);

        when(sourceSystemMapper.detectSourceSystem(eq("GOOGLE_FORMS"), any()))
            .thenReturn(SourceSystemConstant.THIRD_PARTY);
        when(dynamicPayloadParser.parse(eq(SourceSystemConstant.THIRD_PARTY), any()))
            .thenReturn(canonical);
        when(identityResolutionService.resolve(any(CanonicalIdentity.class)))
            .thenReturn(matchedResponse("GOLDEN-103", "TIER_2", 0.75));

        ApiGatewayResponse response = apiGatewayService.process(buildRequest("GOOGLE_FORMS", payload));

        assertNotNull(response);
        assertTrue(response.isSuccess());

        log.info("3rd-party form payload test PASSED");
    }

    /**
     * Test: Dynamic Payload Parsing - Flexible JSON from two different sources
     */
    @Test
    @DisplayName("Dynamic Payload Parsing - Flexible JSON")
    public void testDynamicPayloadParsing() {
        log.info("Test: Dynamic payload parsing");

        Map<String, Object> payload1 = new HashMap<>();
        payload1.put("field1", "value1");

        Map<String, Object> payload2 = new HashMap<>();
        payload2.put("different_field", "different_value");

        CanonicalIdentity c1 = new CanonicalIdentity();
        c1.setSourceSystem(SourceSystemConstant.EVENT_SYSTEM);
        CanonicalIdentity c2 = new CanonicalIdentity();
        c2.setSourceSystem(SourceSystemConstant.THIRD_PARTY);

        when(sourceSystemMapper.detectSourceSystem(eq("SOURCE1"), any())).thenReturn(SourceSystemConstant.EVENT_SYSTEM);
        when(sourceSystemMapper.detectSourceSystem(eq("SOURCE2"), any())).thenReturn(SourceSystemConstant.THIRD_PARTY);
        when(dynamicPayloadParser.parse(eq(SourceSystemConstant.EVENT_SYSTEM), any())).thenReturn(c1);
        when(dynamicPayloadParser.parse(eq(SourceSystemConstant.THIRD_PARTY), any())).thenReturn(c2);
        when(identityResolutionService.resolve(any(CanonicalIdentity.class))).thenReturn(matchedResponse("G1", "TIER_1", 1.0));

        ApiGatewayResponse r1 = apiGatewayService.process(buildRequest("SOURCE1", payload1));
        ApiGatewayResponse r2 = apiGatewayService.process(buildRequest("SOURCE2", payload2));

        assertNotNull(r1);
        assertNotNull(r2);

        log.info("Dynamic payload parsing test PASSED");
    }

    /**
     * Test: Error Handling - parser throws exception
     */
    @Test
    @DisplayName("Error Handling - Parser Exception Returns Error Response")
    public void testErrorHandling() {
        log.info("Test: Error handling");

        Map<String, Object> payload = new HashMap<>();
        payload.put("bad", "data");

        when(sourceSystemMapper.detectSourceSystem(eq("TEST"), any())).thenReturn(SourceSystemConstant.THIRD_PARTY);
        doThrow(new RuntimeException("Simulated parse error"))
            .when(payloadValidator).validate(any(), any());

        ApiGatewayResponse response = apiGatewayService.process(buildRequest("TEST", payload));

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("ERROR", response.getStatus());

        log.info("Error handling test PASSED");
    }
}
