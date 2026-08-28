# Phase 1: Comprehensive Testing Guide

## 🎯 Overview

This guide covers **all testing strategies** for Phase 1 Identity Resolution Engine:
- Unit testing (individual services)
- Integration testing (database layer)
- API testing (REST endpoints)
- End-to-end testing (full workflows)
- Manual testing (JSP dashboard)
- Performance testing (latency & throughput)

---

## 📋 Test Environment Setup

### **Prerequisites**

```bash
# Install required tools
- Java 11+
- Maven 3.6+
- Oracle 11g/12c or Docker Oracle
- Tomcat 9.0+
- Postman or cURL
- Git

# Clone the repository
git clone https://github.com/isharryfung/ire-poc.git
cd ire-poc

# Build the project
mvn clean install

# Run tests
mvn test                    # Unit + Integration tests
mvn verify                  # All tests including E2E
```

---

## 🏃 Quick Start: Run All Tests

```bash
# 1. Start Oracle database (Docker)
docker-compose up -d oracle

# 2. Wait for Oracle to be ready
docker exec oracle sqlplus -v

# 3. Run all tests
mvn clean test

# 4. Check test report
open target/surefire-reports/index.html
```

---

## 🧪 Unit Testing (Service Layer)

### **1. Identity Resolution Service Tests**

**File:** `src/test/java/org/hkust/ire/db/persistence/service/IdentityResolutionServiceTest.java`

```java
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

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.dto.IdentityMatchRequest;
import org.hkust.ire.dto.IdentityMatchResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Identity Resolution Service Tests")
public class IdentityResolutionServiceTest {

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private WaterfallMatchingEngine matchingEngine;

    @InjectMocks
    private IdentityResolutionService identityResolutionService;

    @BeforeEach
    public void setUp() {
        // Test data setup
    }

    @Test
    @DisplayName("Should resolve identity with TIER-1 exact match (100% confidence)")
    public void testTier1ExactMatch() {
        // Arrange
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setHkid("A123456789");
        request.setSource("ADMS");

        IdentityDAO existingIdentity = new IdentityDAO();
        existingIdentity.setId(1L);
        existingIdentity.setHkid("A123456789");

        when(identityRepository.findByHkid("A123456789"))
            .thenReturn(existingIdentity);

        // Act
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // Assert
        assertNotNull(response);
        assertEquals(1.0, response.getConfidence());
        assertEquals("TIER_1_MATCH", response.getMatchTier());
        assertEquals(1L, response.getIdentityId());
        verify(identityRepository, times(1)).findByHkid("A123456789");
    }

    @Test
    @DisplayName("Should resolve identity with TIER-2 email + mobile match (95% confidence)")
    public void testTier2EmailMobileMatch() {
        // Arrange
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setMobile("98765432");
        request.setSource("EVENT_SYSTEM");

        IdentityDAO existingIdentity = new IdentityDAO();
        existingIdentity.setId(2L);
        existingIdentity.setEmail("john@example.com");
        existingIdentity.setMobile("98765432");

        when(matchingEngine.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_MATCH", 0.95, existingIdentity));

        // Act
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // Assert
        assertEquals(0.95, response.getConfidence());
        assertEquals("TIER_2_MATCH", response.getMatchTier());
        assertTrue(response.isAutoMergeEligible());  // >= 95%
    }

    @Test
    @DisplayName("Should route to manual review when confidence < 95%")
    public void testTier3ManualReviewRouting() {
        // Arrange
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("john@example.com");
        request.setName("John Doe");
        request.setSource("GOOGLE_FORMS");  // Low credibility

        // Email + Name match = 90%, but source credibility 0.8x = 72%
        when(matchingEngine.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "TIER_2_MATCH", 0.72, null));

        // Act
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // Assert
        assertEquals(0.72, response.getConfidence());
        assertFalse(response.isAutoMergeEligible());  // < 95%
        assertEquals("TIER_3_MANUAL_REVIEW", response.getMatchTier());
    }

    @Test
    @DisplayName("Should create new identity when no match found")
    public void testCreateNewIdentity() {
        // Arrange
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("newuser@example.com");
        request.setName("New User");

        when(matchingEngine.performProbabilisticMatch(request))
            .thenReturn(new MatchingEngineService.MatchResult(
                "NO_MATCH", 0.0, null));

        // Act
        IdentityMatchResponse response = identityResolutionService.resolveIdentity(request);

        // Assert
        assertEquals(0.0, response.getConfidence());
        assertEquals("NO_MATCH", response.getMatchTier());
        assertNotNull(response.getNewIdentityData());
    }

    @Test
    @DisplayName("Should apply source credibility multiplier correctly")
    public void testSourceCredibilityMultiplier() {
        // Arrange: 90% base × 0.8x multiplier = 72%
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setName("User");
        request.setSource("THIRD_PARTY_FORM");  // 0.8x multiplier

        // Act
        double baseScore = 0.90;
        double credibilityMultiplier = 0.8;
        double finalScore = baseScore * credibilityMultiplier;

        // Assert
        assertEquals(0.72, finalScore);
        assertTrue(finalScore < 0.95);  // Should go to manual review
    }
}
```

---

### **2. Waterfall Matching Engine Tests**

**File:** `src/test/java/org/hkust/ire/db/persistence/service/matching/WaterfallMatchingEngineTest.java`

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Waterfall Matching Engine Tests")
public class WaterfallMatchingEngineTest {

    @InjectMocks
    private WaterfallMatchingEngine engine;

    @Test
    @DisplayName("TIER-1: 100% confidence on Alumni ID match")
    public void testTier1AlumniIdMatch() {
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setAlumniId("HKUST20150001");
        
        MatchingEngineService.MatchResult result = 
            engine.performDeterministicMatch(request);
        
        assertEquals(1.0, result.getConfidence());
        assertEquals("TIER_1_ALUMNI_ID", result.getTierName());
    }

    @Test
    @DisplayName("TIER-1: 100% confidence on HKID match")
    public void testTier1HkidMatch() {
        // Similar to Alumni ID
    }

    @Test
    @DisplayName("TIER-2: 95% confidence on Email + Mobile match")
    public void testTier2EmailMobileMatch() {
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        
        double score = engine.calculateEmailMobileScore(request);
        
        assertEquals(0.95, score);
    }

    @Test
    @DisplayName("TIER-2: 90% confidence on Email + Name match (with fuzzy)")
    public void testTier2EmailNameMatch() {
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setName("John Doe");
        
        double score = engine.calculateEmailNameScore(request);
        
        assertTrue(score >= 0.85 && score <= 0.95);
    }

    @Test
    @DisplayName("TIER-2: 75% confidence on Email-only match")
    public void testTier2EmailOnlyMatch() {
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        
        double score = engine.calculateEmailOnlyScore(request);
        
        assertEquals(0.75, score);
    }

    @Test
    @DisplayName("TIER-3: 0% confidence on Name-only match (insufficient)")
    public void testTier3NameOnlyMatch() {
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setName("John Doe");
        
        double score = engine.calculateNameOnlyScore(request);
        
        assertEquals(0.0, score);
    }

    @Test
    @DisplayName("Should stop evaluation after first match (waterfall principle)")
    public void testEarlyExitWaterfall() {
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setAlumniId("HKUST20150001");
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        
        MatchingEngineService.MatchResult result = 
            engine.performDeterministicMatch(request);
        
        // Should match on Alumni ID (first rule) and stop
        assertEquals("TIER_1_ALUMNI_ID", result.getTierName());
        assertEquals(1.0, result.getConfidence());
        // Should NOT evaluate email/mobile
    }
}
```

---

### **3. Source Credibility Scorer Tests**

**File:** `src/test/java/org/hkust/ire/db/persistence/service/matching/SourceCredibilityScorerTest.java`

```java
@DisplayName("Source Credibility Scorer Tests")
public class SourceCredibilityScorerTest {

    @InjectMocks
    private SourceCredibilityScorer scorer;

    @Test
    @DisplayName("High Trust source (1.0x): ADMS")
    public void testHighTrustAdms() {
        double multiplier = scorer.getCredibilityMultiplier("ADMS");
        assertEquals(1.0, multiplier);
    }

    @Test
    @DisplayName("High Trust source (1.0x): Attendance system")
    public void testHighTrustAttendance() {
        double multiplier = scorer.getCredibilityMultiplier("ATTENDANCE");
        assertEquals(1.0, multiplier);
    }

    @Test
    @DisplayName("Low Trust source (0.8x): Google Forms")
    public void testLowTrustGoogleForms() {
        double multiplier = scorer.getCredibilityMultiplier("GOOGLE_FORMS");
        assertEquals(0.8, multiplier);
    }

    @Test
    @DisplayName("Low Trust source (0.8x): Third-party ticketing")
    public void testLowTrust3rdParty() {
        double multiplier = scorer.getCredibilityMultiplier("THIRD_PARTY_TICKETING");
        assertEquals(0.8, multiplier);
    }

    @Test
    @DisplayName("Example: 90% base × 0.8x multiplier = 72% final (manual review)")
    public void testCredibilityMultiplierExample() {
        double baseScore = 0.90;
        double multiplier = scorer.getCredibilityMultiplier("GOOGLE_FORMS");
        double finalScore = baseScore * multiplier;
        
        assertEquals(0.72, finalScore);
        assertFalse(finalScore >= 0.95);  // Not auto-mergeable
    }
}
```

---

## 🔗 Integration Testing (Database Layer)

### **4. Identity Repository Tests**

**File:** `src/test/java/org/hkust/ire/db/persistence/repository/IdentityRepositoryTest.java`

```java
@ExtendWith(SpringExtension.class)
@DataJpaTest
@DisplayName("Identity Repository Integration Tests")
public class IdentityRepositoryTest {

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should find identity by email")
    public void testFindByEmail() {
        // Arrange
        IdentityDAO identity = new IdentityDAO();
        identity.setEmail("john@example.com");
        identity.setName("John Doe");
        entityManager.persistAndFlush(identity);

        // Act
        IdentityDAO found = identityRepository.findByEmail("john@example.com");

        // Assert
        assertNotNull(found);
        assertEquals("john@example.com", found.getEmail());
        assertEquals("John Doe", found.getName());
    }

    @Test
    @DisplayName("Should find identity by HKID")
    public void testFindByHkid() {
        IdentityDAO identity = new IdentityDAO();
        identity.setHkid("A123456789");
        identity.setEmail("user@example.com");
        entityManager.persistAndFlush(identity);

        IdentityDAO found = identityRepository.findByHkid("A123456789");

        assertNotNull(found);
        assertEquals("A123456789", found.getHkid());
    }

    @Test
    @DisplayName("Should find identity by mobile number")
    public void testFindByMobile() {
        IdentityDAO identity = new IdentityDAO();
        identity.setMobile("98765432");
        identity.setEmail("user@example.com");
        entityManager.persistAndFlush(identity);

        IdentityDAO found = identityRepository.findByMobile("98765432");

        assertNotNull(found);
        assertEquals("98765432", found.getMobile());
    }

    @Test
    @DisplayName("Should not find non-existent identity")
    public void testFindNonExistent() {
        IdentityDAO found = identityRepository.findByEmail("nonexistent@example.com");
        assertNull(found);
    }

    @Test
    @DisplayName("Should update identity")
    public void testUpdateIdentity() {
        // Create and persist
        IdentityDAO identity = new IdentityDAO();
        identity.setEmail("original@example.com");
        identity.setName("Original Name");
        identityRepository.save(identity);

        // Update
        identity.setName("Updated Name");
        identityRepository.save(identity);

        // Verify
        IdentityDAO updated = identityRepository.findByEmail("original@example.com");
        assertEquals("Updated Name", updated.getName());
    }
}
```

---

### **5. Identity Graph Repository Tests**

**File:** `src/test/java/org/hkust/ire/db/persistence/repository/IdentityGraphRepositoryTest.java`

```java
@DataJpaTest
@DisplayName("Identity Graph Repository Tests")
public class IdentityGraphRepositoryTest {

    @Autowired
    private IdentityGraphRepository graphRepository;

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    @DisplayName("Should link two identities together")
    public void testLinkIdentities() {
        // Day 1: Create identity 1
        IdentityDAO identity1 = new IdentityDAO();
        identity1.setEmail("user@example.com");
        identity1.setName("John Doe");
        identityRepository.save(identity1);

        // Day 2: Create identity 2 (same person, different source)
        IdentityDAO identity2 = new IdentityDAO();
        identity2.setMobile("98765432");
        identity2.setName("John Doe");
        identityRepository.save(identity2);

        // Create graph link
        IdentityGraphDAO graphLink = new IdentityGraphDAO();
        graphLink.setSourceIdentityId(identity1.getId());
        graphLink.setTargetIdentityId(identity2.getId());
        graphLink.setRelationType("DUPLICATE");
        graphLink.setConfidence(0.95);
        graphRepository.save(graphLink);

        // Verify link
        IdentityGraphDAO found = graphRepository
            .findBySourceAndTarget(identity1.getId(), identity2.getId());
        
        assertNotNull(found);
        assertEquals("DUPLICATE", found.getRelationType());
        assertEquals(0.95, found.getConfidence());
    }

    @Test
    @DisplayName("Should find all related identities")
    public void testFindAllRelated() {
        // Create hub identity
        IdentityDAO hub = new IdentityDAO();
        hub.setEmail("hub@example.com");
        identityRepository.save(hub);

        // Create 3 related identities
        for (int i = 1; i <= 3; i++) {
            IdentityDAO related = new IdentityDAO();
            related.setEmail("related" + i + "@example.com");
            identityRepository.save(related);

            IdentityGraphDAO link = new IdentityGraphDAO();
            link.setSourceIdentityId(hub.getId());
            link.setTargetIdentityId(related.getId());
            link.setRelationType("DUPLICATE");
            graphRepository.save(link);
        }

        // Find all related
        List<IdentityGraphDAO> relatedIdentities = 
            graphRepository.findBySourceIdentityId(hub.getId());

        assertEquals(3, relatedIdentities.size());
    }
}
```

---

## 🌐 API Testing (REST Endpoints)

### **6. Identity Controller API Tests**

**File:** `src/test/java/org/hkust/ire/web/controller/IdentityControllerTest.java`

```java
@ExtendWith(SpringExtension.class)
@WebMvcTest(IdentityController.class)
@DisplayName("Identity Controller API Tests")
public class IdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityResolutionService identityResolutionService;

    @Test
    @DisplayName("POST /ire/api/identities/resolve - TIER-1 match")
    public void testResolveIdentityTier1() throws Exception {
        // Arrange
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setHkid("A123456789");
        request.setSource("ADMS");

        IdentityMatchResponse response = new IdentityMatchResponse();
        response.setIdentityId(1L);
        response.setConfidence(1.0);
        response.setMatchTier("TIER_1_MATCH");
        response.setAutoMergeEligible(true);

        when(identityResolutionService.resolveIdentity(any()))
            .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/ire/api/identities/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.identityId").value(1))
            .andExpect(jsonPath("$.confidence").value(1.0))
            .andExpect(jsonPath("$.matchTier").value("TIER_1_MATCH"));
    }

    @Test
    @DisplayName("POST /ire/api/identities/resolve - TIER-2 match (95%)")
    public void testResolveIdentityTier2() throws Exception {
        // Similar structure
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setMobile("98765432");
        request.setSource("EVENT_SYSTEM");

        IdentityMatchResponse response = new IdentityMatchResponse();
        response.setIdentityId(2L);
        response.setConfidence(0.95);
        response.setMatchTier("TIER_2_MATCH");
        response.setAutoMergeEligible(true);

        when(identityResolutionService.resolveIdentity(any()))
            .thenReturn(response);

        mockMvc.perform(post("/ire/api/identities/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confidence").value(0.95));
    }

    @Test
    @DisplayName("POST /ire/api/identities/resolve - TIER-3 manual review (72%)")
    public void testResolveIdentityTier3ManualReview() throws Exception {
        IdentityMatchRequest request = new IdentityMatchRequest();
        request.setEmail("user@example.com");
        request.setName("User");
        request.setSource("GOOGLE_FORMS");

        IdentityMatchResponse response = new IdentityMatchResponse();
        response.setConfidence(0.72);
        response.setMatchTier("TIER_3_MANUAL_REVIEW");
        response.setAutoMergeEligible(false);

        when(identityResolutionService.resolveIdentity(any()))
            .thenReturn(response);

        mockMvc.perform(post("/ire/api/identities/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confidence").value(0.72))
            .andExpect(jsonPath("$.matchTier").value("TIER_3_MANUAL_REVIEW"))
            .andExpect(jsonPath("$.autoMergeEligible").value(false));
    }

    @Test
    @DisplayName("GET /ire - Home page")
    public void testHomePage() throws Exception {
        mockMvc.perform(get("/ire"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"));
    }

    private String asJsonString(Object obj) throws Exception {
        return new ObjectMapper().writeValueAsString(obj);
    }
}
```

---

## 🧬 End-to-End Testing (Full Workflow)

### **7. Identity Resolution E2E Test**

**File:** `src/test/java/org/hkust/ire/integration/IdentityResolutionE2ETest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Identity Resolution E2E Tests")
public class IdentityResolutionE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IdentityRepository identityRepository;

    private String baseUrl;

    @BeforeEach
    public void setUp() {
        baseUrl = "http://localhost:" + port + "/ire/api";
        identityRepository.deleteAll();
    }

    @Test
    @DisplayName("E2E: Three-day identity learning process")
    public void testThreeDayIdentityLearning() throws Exception {
        // Day 1: Event system sends Email only
        IdentityMatchRequest day1Request = new IdentityMatchRequest();
        day1Request.setEmail("john.doe@example.com");
        day1Request.setName("John Doe");
        day1Request.setSource("EVENT_SYSTEM");

        ResponseEntity<IdentityMatchResponse> day1Response = restTemplate.postForEntity(
            baseUrl + "/identities/resolve",
            day1Request,
            IdentityMatchResponse.class
        );

        assertEquals(HttpStatus.OK, day1Response.getStatusCode());
        assertFalse(day1Response.getBody().isAutoMergeEligible());  // Needs review
        Long identityId = day1Response.getBody().getIdentityId();

        // Day 2: Attendance system confirms with Smart Card ID
        IdentityMatchRequest day2Request = new IdentityMatchRequest();
        day2Request.setSmartCardId("STAFF20150001");
        day2Request.setEmail("john.doe@example.com");
        day2Request.setSource("ATTENDANCE");

        ResponseEntity<IdentityMatchResponse> day2Response = restTemplate.postForEntity(
            baseUrl + "/identities/resolve",
            day2Request,
            IdentityMatchResponse.class
        );

        assertEquals(HttpStatus.OK, day2Response.getStatusCode());
        // Should now have higher confidence (email + smart card)

        // Day 3: 3rd-party form sends Mobile + Name
        IdentityMatchRequest day3Request = new IdentityMatchRequest();
        day3Request.setMobile("98765432");
        day3Request.setName("John Doe");
        day3Request.setSource("GOOGLE_FORMS");

        ResponseEntity<IdentityMatchResponse> day3Response = restTemplate.postForEntity(
            baseUrl + "/identities/resolve",
            day3Request,
            IdentityMatchResponse.class
        );

        assertEquals(HttpStatus.OK, day3Response.getStatusCode());
        // Should recognize as same person via graph learning
    }

    @Test
    @DisplayName("E2E: Source credibility affects routing decision")
    public void testSourceCredibilityRouting() throws Exception {
        // Internal source (high trust) - same match, different routing
        IdentityMatchRequest internalRequest = new IdentityMatchRequest();
        internalRequest.setEmail("user@example.com");
        internalRequest.setName("User");
        internalRequest.setSource("ADMS");  // 1.0x multiplier

        ResponseEntity<IdentityMatchResponse> internalResponse = restTemplate.postForEntity(
            baseUrl + "/identities/resolve",
            internalRequest,
            IdentityMatchResponse.class
        );

        assertTrue(internalResponse.getBody().getConfidence() >= 0.95);
        assertTrue(internalResponse.getBody().isAutoMergeEligible());

        // External source (low trust) - same match, different routing
        IdentityMatchRequest externalRequest = new IdentityMatchRequest();
        externalRequest.setEmail("user@example.com");
        externalRequest.setName("User");
        externalRequest.setSource("GOOGLE_FORMS");  // 0.8x multiplier

        ResponseEntity<IdentityMatchResponse> externalResponse = restTemplate.postForEntity(
            baseUrl + "/identities/resolve",
            externalRequest,
            IdentityMatchResponse.class
        );

        assertTrue(externalResponse.getBody().getConfidence() < 0.95);
        assertFalse(externalResponse.getBody().isAutoMergeEligible());
        assertEquals("TIER_3_MANUAL_REVIEW", externalResponse.getBody().getMatchTier());
    }
}
```

---

## 🖥️ Manual Testing (JSP Dashboard)

### **8. JSP Dashboard Manual Testing**

#### **A. Start Tomcat & Access Dashboard**

```bash
# 1. Build WAR file
mvn clean package

# 2. Deploy to Tomcat
cp target/ire.war $TOMCAT_HOME/webapps/

# 3. Start Tomcat
$TOMCAT_HOME/bin/startup.sh

# 4. Access dashboard
http://localhost:8080/ire

# 5. Check logs
tail -f $TOMCAT_HOME/logs/catalina.out
```

#### **B. Test Home Page**

- [ ] Access `http://localhost:8080/ire`
- [ ] See welcome message
- [ ] See navigation menu (Identity, Reviews, Admin)
- [ ] Verify page styling (Bootstrap)
- [ ] Check browser console for errors

#### **C. Test Identity Resolution Page**

```html
<!-- Manually test these scenarios -->

Scenario 1: TIER-1 Match
- Enter HKID: A123456789
- Source: ADMS
- Expected: Green "100% Confidence - Auto Merge"

Scenario 2: TIER-2 Match (95%)
- Enter Email: user@example.com
- Enter Mobile: 98765432
- Source: EVENT_SYSTEM
- Expected: Green "95% Confidence - Auto Merge"

Scenario 3: TIER-3 Manual Review
- Enter Email: user@example.com
- Enter Name: User Doe
- Source: GOOGLE_FORMS
- Expected: Yellow "72% Confidence - Manual Review Required"

Scenario 4: No Match
- Enter Email: nobody@nonexistent.com
- Source: UNKNOWN
- Expected: "No match found - Create new identity"
```

#### **D. Test Review Queue Dashboard**

- [ ] Navigate to "Reviews" tab
- [ ] See pending reviews list
- [ ] Click on a review to see details
- [ ] See match reasons and field scoring
- [ ] Approve match button
- [ ] Reject match button
- [ ] Merge with custom rules button

#### **E. Test Admin Monitoring Dashboard**

- [ ] Navigate to "Admin" > "Monitoring"
- [ ] See system metrics:
  - Total identities matched
  - Success rate by tier
  - Average confidence score
  - Review queue depth
  - API latency (p50, p95, p99)
- [ ] See graphs updating in real-time
- [ ] Verify data accuracy

---

## 📊 Performance Testing

### **9. Load Testing with JMeter**

**File:** `src/test/jmeter/IdentityResolution.jmx`

```bash
# Install JMeter
brew install jmeter

# Run load test
jmeter -n -t src/test/jmeter/IdentityResolution.jmx \
    -l results.jtl \
    -j jmeter.log \
    -Jusers=100 \
    -Jrampup=10 \
    -Jduration=60

# View results
jmeter -g results.jtl -o html_report
```

**Test Plan:**
- 100 concurrent users
- Ramp-up: 10 seconds
- Duration: 60 seconds
- API endpoint: `POST /ire/api/identities/resolve`

**Success Criteria:**
- [ ] p99 latency < 1000ms
- [ ] p95 latency < 500ms
- [ ] p50 latency < 100ms
- [ ] Error rate < 1%
- [ ] Throughput > 50 req/sec

---

### **10. API Testing with Postman**

#### **Import Postman Collection**

```bash
# Download collection from GitHub
curl -O https://raw.githubusercontent.com/isharryfung/ire-poc/main/postman/IRE_Phase1.postman_collection.json

# Import into Postman
# Open Postman → Import → Select JSON file
```

#### **Postman Test Cases**

**Test 1: TIER-1 Exact Match (HKID)**
```
POST http://localhost:8080/ire/api/identities/resolve
Content-Type: application/json

{
  "hkid": "A123456789",
  "source": "ADMS"
}

Expected Response:
{
  "identityId": 1,
  "confidence": 1.0,
  "matchTier": "TIER_1_HKID",
  "autoMergeEligible": true,
  "fieldScoring": {
    "hkid": {
      "matched": true,
      "score": 100
    }
  }
}
```

**Test 2: TIER-2 Email + Mobile (95%)**
```
POST http://localhost:8080/ire/api/identities/resolve
Content-Type: application/json

{
  "email": "john@example.com",
  "mobile": "98765432",
  "source": "EVENT_SYSTEM"
}

Expected Response:
{
  "identityId": 2,
  "confidence": 0.95,
  "matchTier": "TIER_2_EMAIL_MOBILE",
  "autoMergeEligible": true,
  "fieldScoring": {
    "email": {"matched": true, "score": 40},
    "mobile": {"matched": true, "score": 30},
    "name": {"matched": false, "score": 0},
    "dob": {"matched": false, "score": 0}
  }
}
```

**Test 3: TIER-3 Manual Review (Source Credibility)**
```
POST http://localhost:8080/ire/api/identities/resolve
Content-Type: application/json

{
  "email": "user@example.com",
  "name": "User Doe",
  "source": "GOOGLE_FORMS"
}

Expected Response:
{
  "identityId": null,
  "confidence": 0.72,
  "matchTier": "TIER_3_MANUAL_REVIEW",
  "autoMergeEligible": false,
  "reviewId": "REV-12345",
  "reasons": [
    "Email + Name match = 90% base",
    "Source GOOGLE_FORMS credibility = 0.8x",
    "Final score: 90% × 0.8 = 72% < 95% threshold",
    "Routed to manual review"
  ]
}
```

---

## 🧪 Test Execution Commands

### **Run All Tests**

```bash
# Unit + Integration + API tests
mvn clean test

# With coverage report
mvn clean test jacoco:report
open target/site/jacoco/index.html

# Only specific test class
mvn test -Dtest=IdentityResolutionServiceTest

# Only specific test method
mvn test -Dtest=IdentityResolutionServiceTest#testTier1ExactMatch
```

### **Run Integration Tests**

```bash
# Integration tests only
mvn clean verify -P integration-tests

# With database
mvn clean verify -Ddb.profile=oracle
```

### **Run E2E Tests**

```bash
# Full end-to-end
mvn clean verify -P e2e-tests

# Against remote environment
mvn clean verify -P e2e-tests -Dbase.url=http://production-url
```

---

## 📋 Test Checklist

### **Phase 1 Testing Checklist**

- [ ] **Unit Tests**
  - [ ] Identity Resolution Service (6 tests)
  - [ ] Waterfall Matching Engine (7 tests)
  - [ ] Source Credibility Scorer (5 tests)
  - [ ] Confidence Calculator (4 tests)
  - [ ] API Gateway Service (5 tests)
  - [ ] Manual Review Service (4 tests)

- [ ] **Integration Tests**
  - [ ] Identity Repository (5 tests)
  - [ ] Identity Graph Repository (4 tests)
  - [ ] Manual Review Repository (4 tests)
  - [ ] Audit Log Repository (3 tests)

- [ ] **API Tests**
  - [ ] POST /api/identities/resolve (5 scenarios)
  - [ ] GET /api/identities/{id}
  - [ ] GET /api/reviews/queue
  - [ ] POST /api/reviews/{id}/approve
  - [ ] POST /api/reviews/{id}/reject
  - [ ] GET /api/health

- [ ] **E2E Tests**
  - [ ] Three-day identity learning
  - [ ] Source credibility routing
  - [ ] Auto-merge workflow
  - [ ] Manual review workflow
  - [ ] CRM inbox notification

- [ ] **Manual Testing**
  - [ ] Home page load
  - [ ] Identity resolution form
  - [ ] Review queue display
  - [ ] Admin monitoring dashboard
  - [ ] Error handling

- [ ] **Performance Testing**
  - [ ] Latency < 100ms (p50)
  - [ ] Latency < 500ms (p95)
  - [ ] Latency < 1000ms (p99)
  - [ ] Throughput > 50 req/sec
  - [ ] Error rate < 1%

- [ ] **Documentation**
  - [ ] All tests documented
  - [ ] Test data clearly defined
  - [ ] Expected outputs specified
  - [ ] Known issues logged

---

## 🐛 Debugging Tips

### **Enable Debug Logging**

```properties
# application-test.properties
logging.level.root=INFO
logging.level.org.hkust.ire=DEBUG
logging.level.org.springframework=DEBUG
logging.level.org.hibernate=DEBUG
```

### **Database Inspection**

```bash
# Connect to Oracle test database
sqlplus -u test_user/test_password@//localhost:1521/TESTDB

# View identity table
SELECT * FROM identities;
SELECT * FROM identity_links;
SELECT * FROM identity_graph;
```

### **Mock Data Seeding**

```sql
-- seed-test-data.sql
INSERT INTO identities (email, mobile, hkid, name, dob)
VALUES ('john@example.com', '98765432', 'A123456789', 'John Doe', '1990-01-01');

INSERT INTO source_credibility (source_name, multiplier)
VALUES ('ADMS', 1.0),
       ('ATTENDANCE', 1.0),
       ('GOOGLE_FORMS', 0.8);
```

---

## 📈 Test Report Generation

```bash
# Generate test report
mvn clean test surefire-report:report

# Generate code coverage
mvn clean test jacoco:report

# Open in browser
open target/site/surefire-report.html
open target/site/jacoco/index.html
```

---

## ✅ Success Criteria

Your Phase 1 testing is **COMPLETE** when:

✅ All 40+ unit tests pass
✅ All 16+ integration tests pass
✅ All 5 API test scenarios pass
✅ All 5 E2E workflows pass
✅ All manual testing checks complete
✅ Performance benchmarks met
✅ Test coverage > 80%
✅ No critical bugs found
✅ Ready for staging deployment

---

**Next Steps:**

1. 🏃 Run all tests: `mvn clean test`
2. 📊 Check coverage: `mvn jacoco:report`
3. 🧪 Manual test via browser: `http://localhost:8080/ire`
4. 📈 Load test: `jmeter -n -t src/test/jmeter/IdentityResolution.jmx`
5. ✅ Approve for production

