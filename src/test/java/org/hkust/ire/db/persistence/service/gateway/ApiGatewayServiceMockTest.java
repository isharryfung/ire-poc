package org.hkust.ire.db.persistence.service.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hkust.ire.dto.ApiGatewayRequest;
import org.hkust.ire.dto.CanonicalIdentity;

/**
 * Mock-based unit tests for API Gateway Service
 * 
 * Tests flexible JSON parsing from multiple sources
 * 
 * @author isharryfung
 * @since 2026-05-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API Gateway Service - Mock Tests")
public class ApiGatewayServiceMockTest {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayServiceMockTest.class);

    @Mock
    private DynamicPayloadParser payloadParser;

    @Mock
    private SourceSystemMapper sourceSystemMapper;

    @InjectMocks
    private ApiGatewayService apiGatewayService;

    @BeforeEach
    public void setUp() {
        log.info("Setting up API Gateway Service tests");
    }

    /**
     * Test: Parse Event System Payload (Email + Name)
     */
    @Test
    @DisplayName("Parse Event System Payload (Email + Name)")
    public void testParseEventSystemPayload() {
        log.info("Test: Parse Event System payload");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("EVENT_SYSTEM");
        request.setPayload("{\"email\": \"john@example.com\", \"name\": \"John Doe\"}");

        CanonicalIdentity canonical = apiGatewayService.parseAndNormalize(request);

        assertNotNull(canonical);
        assertEquals("john@example.com", canonical.getEmail());
        assertEquals("John Doe", canonical.getName());
        assertEquals("EVENT_SYSTEM", canonical.getSourceSystem());

        log.info("✅ Event System payload test PASSED");
    }

    /**
     * Test: Parse Attendance System Payload (Smart Card ID)
     */
    @Test
    @DisplayName("Parse Attendance System Payload (Smart Card ID)")
    public void testParseAttendancePayload() {
        log.info("Test: Parse Attendance payload");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("ATTENDANCE");
        request.setPayload("{\"smart_card_id\": \"STAFF20150001\"}");

        CanonicalIdentity canonical = apiGatewayService.parseAndNormalize(request);

        assertNotNull(canonical);
        assertEquals("STAFF20150001", canonical.getSmartCardId());
        assertEquals("ATTENDANCE", canonical.getSourceSystem());

        log.info("✅ Attendance payload test PASSED");
    }

    /**
     * Test: Parse 3rd-Party Form Payload (Mobile + Name)
     */
    @Test
    @DisplayName("Parse 3rd-Party Form Payload (Mobile + Name)")
    public void testParse3rdPartyFormPayload() {
        log.info("Test: Parse 3rd-party form payload");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("GOOGLE_FORMS");
        request.setPayload("{\"mobile\": \"98765432\", \"name\": \"User Doe\"}");

        CanonicalIdentity canonical = apiGatewayService.parseAndNormalize(request);

        assertNotNull(canonical);
        assertEquals("98765432", canonical.getMobile());
        assertEquals("User Doe", canonical.getName());
        assertEquals("GOOGLE_FORMS", canonical.getSourceSystem());

        log.info("✅ 3rd-party form payload test PASSED");
    }

    /**
     * Test: Dynamic Payload Parsing - Flexible JSON
     */
    @Test
    @DisplayName("Dynamic Payload Parsing - Flexible JSON")
    public void testDynamicPayloadParsing() {
        log.info("Test: Dynamic payload parsing");

        ApiGatewayRequest request1 = new ApiGatewayRequest();
        request1.setSource("SOURCE1");
        request1.setPayload("{\"field1\": \"value1\", \"field2\": \"value2\"}");

        ApiGatewayRequest request2 = new ApiGatewayRequest();
        request2.setSource("SOURCE2");
        request2.setPayload("{\"different_field\": \"different_value\"}");

        CanonicalIdentity canonical1 = apiGatewayService.parseAndNormalize(request1);
        CanonicalIdentity canonical2 = apiGatewayService.parseAndNormalize(request2);

        assertNotNull(canonical1);
        assertNotNull(canonical2);

        log.info("✅ Dynamic payload parsing test PASSED");
    }

    /**
     * Test: Invalid Payload Handling
     */
    @Test
    @DisplayName("Invalid Payload Handling")
    public void testInvalidPayloadHandling() {
        log.info("Test: Invalid payload handling");

        ApiGatewayRequest request = new ApiGatewayRequest();
        request.setSource("TEST");
        request.setPayload("{INVALID JSON}");

        assertThrows(Exception.class, () -> {
            apiGatewayService.parseAndNormalize(request);
        });

        log.info("✅ Invalid payload handling test PASSED");
    }
}
