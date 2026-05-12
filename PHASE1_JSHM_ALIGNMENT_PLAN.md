# Phase 1: JSHM Alignment - Full HKUST Standards Implementation

## Overview

This document outlines the complete restructuring of Phase 1 Identity Resolution Engine (IRE) to align with JSHM (Hall System) coding standards, ensuring consistency with existing HKUST projects.

---

## 🎯 Key Alignment Points

### 1. Technology Stack (JSHM-Aligned)

```
✅ Spring Boot 2.1.3 (matching JSHM)
✅ WAR Packaging (Tomcat deployment)
✅ Oracle Database (OJDBC 10)
✅ Hibernate ORM
✅ Spring Security with CAS
✅ Redis for caching
✅ Quartz for batch jobs
✅ JSP views (admin dashboard)
✅ REST APIs (JSON responses)
```

### 2. Project Structure (JSHM Pattern)

```
ire-poc/
├── pom.xml (WAR packaging, Spring Boot 2.1.3)
├── src/
│   ├── main/
│   │   ├── java/org/hkust/ire/
│   │   │   ├── common/
│   │   │   │   ├── constant/           (Constants and enums)
│   │   │   │   ├── exception/          (Custom exceptions)
│   │   │   │   ├── utils/              (Utility classes)
│   │   │   │   └── security/           (Security utilities)
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── CasConfig.java
│   │   │   │   ├── CacheConfig.java
│   │   │   │   ├── OracleDataSourceConfig.java
│   │   │   │   └── WebConfig.java
│   │   │   │
│   │   │   ├── web/
│   │   │   │   └── controller/
│   │   │   │       ├── HomeController.java
│   │   │   │       ├── ApiGatewayController.java
│   │   │   │       ├── IdentityController.java
│   │   │   │       ├── ManualReviewController.java
│   │   │   │       └── HealthCheckController.java
│   │   │   │
│   │   │   ├── db/
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── domain/          (DAO classes with *DAO suffix)
│   │   │   │   │   │   ├── IdentityDAO.java
│   │   │   │   │   │   ├── IdentityLinkDAO.java
│   │   │   │   │   │   ├── ManualReviewDAO.java
│   │   │   │   │   │   └── AuditLogDAO.java
│   │   │   │   │   │
│   │   │   │   │   ├── repository/      (Repository interfaces)
│   │   │   │   │   │   ├── CommonRepository.java
│   │   │   │   │   │   ├── IdentityRepository.java
│   │   │   │   │   │   ├── ManualReviewRepository.java
│   │   │   │   │   │   └── AuditLogRepository.java
│   │   │   │   │   │
│   │   │   │   │   └── service/         (Business logic services)
│   │   │   │   │       ├── gateway/
│   │   │   │   │       │   ├── ApiGatewayService.java
│   │   │   │   │       │   ├── DynamicPayloadParser.java
│   │   │   │   │       │   ├── SourceSystemMapper.java
│   │   │   │   │       │   └── PayloadValidator.java
│   │   │   │   │       │
│   │   │   │   │       ├── matching/
│   │   │   │   │       │   ├── WaterfallMatchingEngine.java
│   │   │   │   │       │   ├── SourceCredibilityScorer.java
│   │   │   │   │       │   ├── ConfidenceCalculator.java
│   │   │   │   │       │   └── MatchingEngineService.java
│   │   │   │   │       │
│   │   │   │   │       ├── review/
│   │   │   │   │       │   ├── ManualReviewService.java
│   │   │   │   │       │   └── ReviewQueueManager.java
│   │   │   │   │       │
│   │   │   │   │       ├── identity/
│   │   │   │   │       │   ├── IdentityResolutionService.java
│   │   │   │   │       │   ├── IdentityMergeService.java
│   │   │   │   │       │   ├── IdentityGraphService.java
│   │   │   │   │       │   └── IdentityCacheService.java
│   │   │   │   │       │
│   │   │   │   │       ├── iam/
│   │   │   │   │       │   ├── IamService.java
│   │   │   │   │       │   └── VerifiedIdentityService.java
│   │   │   │   │       │
│   │   │   │   │       ├── batch/
│   │   │   │   │       │   └── BatchJobService.java
│   │   │   │   │       │
│   │   │   │   │       └── monitoring/
│   │   │   │   │           ├── MetricsService.java
│   │   │   │   │           └── PerformanceMonitor.java
│   │   │   │   │
│   │   │   │   └── CommonRepositoryImpl.java
│   │   │   │
���   │   │   ├── scheduler/
│   │   │   │   └── job/
│   │   │   │       └── IreProcessBatch.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── ApiGatewayRequest.java
│   │   │   │   ├── ApiGatewayResponse.java
│   │   │   │   ├── IdentityMatchRequest.java
│   │   │   │   ├── IdentityMatchResponse.java
│   │   │   │   ├── ManualReviewDTO.java
│   │   │   │   └── CanonicalIdentity.java
│   │   │   │
│   │   │   └── IreApplication.java
│   │   │
│   │   └── webapp/
│   │       ├── WEB-INF/
│   │       │   ├── web.xml
│   │       │   └── spring/
│   │       │       └── applicationContext-security.xml (CAS config)
│   │       │
│   │       └── jsp/
│   │           ├── index.jsp
│   │           ├── dashboard.jsp
│   │           ├── review/
│   │           │   ├── queue.jsp
│   │           │   └── details.jsp
│   │           └── admin/
│   │               └── monitoring.jsp
│   │
│   ├── test/
│   │   └── java/org/hkust/ire/
│   │       ├── db/service/ (test cases)
│   │       ├── web/controller/ (test cases)
│   │       └── integration/ (E2E tests)
│   │
│   └── resources/
│       ├── application.properties (or application.yml)
│       ├── application-dev.properties
│       ├── application-test.properties
│       ├── application-prod.properties
│       ├── logback-spring.xml
│       ├── db/migration/ (Flyway scripts)
│       │   ├── V1__init_identities.sql
│       │   ├── V2__init_identity_links.sql
│       │   ├── V3__init_identity_graph.sql
│       │   ├── V4__init_manual_reviews.sql
│       │   ├── V5__init_audit_logs.sql
│       │   ├── V6__init_source_credibility.sql
│       │   └── V7__init_verified_identities.sql
│       │
│       └── schema/
│           ├── event-system-schema.json
│           ├── attendance-schema.json
│           └── 3rd-party-forms-schema.json
│
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 📝 Coding Style Guidelines (JSHM-Aligned)

### 1. Dependency Injection
```java
// ✅ DO USE (Field Injection - JSHM Style)
@Service
public class IdentityResolutionService {
    @Autowired
    private IdentityRepository identityRepository;
    
    @Autowired
    private MatchingEngineService matchingEngineService;
    
    @Autowired
    private IamService iamService;
}

// ❌ DON'T USE (Constructor Injection - Modern style, not JSHM)
@Service
@RequiredArgsConstructor
public class IdentityResolutionService {
    private final IdentityRepository identityRepository;
}
```

### 2. Logging
```java
// ✅ DO USE (SLF4J LoggerFactory - JSHM Style)
public class IdentityResolutionService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    
    public void resolveIdentity(IdentityMatchRequest request) {
        log.debug("Starting identity resolution for email: {}", request.getEmail());
        log.info("Identity matched with confidence: {}", confidence);
        log.error("Error resolving identity", exception);
    }
}

// ❌ DON'T USE (Lombok @Slf4j - not JSHM)
@Slf4j
public class IdentityResolutionService {
    log.info("...");
}
```

### 3. Entity/DAO Classes
```java
// ✅ DO USE (Traditional DAO with getters/setters - JSHM Style)
@Entity
@Table(name = "identities")
public class IdentityDAO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "hkid")
    private String hkid;
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    // Constructor
    public IdentityDAO() {
    }
    
    public IdentityDAO(String email, String hkid) {
        this.email = email;
        this.hkid = hkid;
    }
}

// ❌ DON'T USE (Lombok @Data, @Builder - not JSHM)
@Entity
@Data
@Builder
public class IdentityDAO {
    // Lombok generates getters/setters
}
```

### 4. Documentation
```java
// ✅ DO USE (Detailed Javadoc - JSHM Style)
/**
 * Service for resolving identities from multiple sources
 * 
 * This service orchestrates the identity matching process, including:
 * - TIER-1 deterministic matching (HKID, Staff/Student ID)
 * - TIER-2 probabilistic matching with source credibility
 * - TIER-3 manual review routing
 * 
 * @author isharray
 * @since 2026-05-12
 * @version 1.0
 */
@Service
public class IdentityResolutionService {
    
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    
    /**
     * Resolve identity from incoming request
     * 
     * Performs waterfall matching:
     * 1. TIER-1: Deterministic matching (100% confidence)
     * 2. TIER-2: Probabilistic matching (95%+ confidence)
     * 3. TIER-3: Manual review for uncertain matches
     * 
     * @param request Identity match request
     * @return IdentityMatchResponse with matched or new identity
     * @throws InvalidPayloadException if request is invalid
     * @see #performDeterministicMatch(IdentityMatchRequest)
     * @see #performProbabilisticMatch(IdentityMatchRequest)
     */
    public IdentityMatchResponse resolveIdentity(IdentityMatchRequest request) {
        log.debug("Resolving identity for email: {}", request.getEmail());
        // Implementation
    }
}

// ❌ DON'T USE (Minimal documentation - not JSHM)
@Service
public class IdentityResolutionService {
    // Resolve identity
    public IdentityMatchResponse resolveIdentity(IdentityMatchRequest request) {
    }
}
```

### 5. Service Implementation
```java
// ✅ DO USE (Service with business logic - JSHM Style)
@Service
public class IdentityResolutionService {
    
    @Autowired
    private IdentityRepository identityRepository;
    
    @Autowired
    private MatchingEngineService matchingEngineService;
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    
    /**
     * Resolve identity with waterfall matching
     */
    public IdentityMatchResponse resolveIdentity(IdentityMatchRequest request) {
        log.info("Starting identity resolution");
        
        try {
            // TIER-1 matching
            MatchingEngineService.MatchResult tier1Result = 
                matchingEngineService.performDeterministicMatch(request);
            
            if (tier1Result.getMatchTier() != null) {
                log.debug("TIER-1 match found");
                // Handle match
            }
            
            // TIER-2 matching
            MatchingEngineService.MatchResult tier2Result = 
                matchingEngineService.performProbabilisticMatch(request);
            
            // ... more logic
            
        } catch (Exception e) {
            log.error("Error in identity resolution", e);
            throw new IdentityResolutionException("Resolution failed", e);
        }
    }
}

// ❌ DON'T USE (Constructor injection, no detailed docs - not JSHM)
@Service
@RequiredArgsConstructor
public class IdentityResolutionService {
    private final IdentityRepository identityRepository;
    
    public IdentityMatchResponse resolveIdentity(IdentityMatchRequest request) {
    }
}
```

### 6. Repository Pattern
```java
// ✅ DO USE (Custom CommonRepository - JSHM Style)
public interface IdentityRepository extends CommonRepository<IdentityDAO, Long> {
    
    @Transactional
    @Query(nativeQuery = true, value = 
        "SELECT * FROM identities WHERE email = :email")
    IdentityDAO findByEmail(@Param("email") String email);
    
    @Transactional
    @Query(nativeQuery = true, value = 
        "SELECT * FROM identities WHERE hkid = :hkid AND is_active = 'Y'")
    List<IdentityDAO> findByHkidActive(@Param("hkid") String hkid);
}

// ❌ DON'T USE (Standard JpaRepository without custom methods)
public interface IdentityRepository extends JpaRepository<IdentityDAO, Long> {
    Optional<IdentityDAO> findByEmail(String email);
}
```

### 7. Controller Style
```java
// ✅ DO USE (Controller with REST endpoints and JSP views - JSHM Style)
@Controller
@RequestMapping("/ire")
public class IdentityController {
    
    @Autowired
    private IdentityResolutionService identityResolutionService;
    
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    
    /**
     * Home page
     */
    @GetMapping("")
    public String home(Model model) {
        log.debug("Loading home page");
        return "index";
    }
    
    /**
     * API endpoint for identity resolution
     */
    @PostMapping("/api/identities/resolve")
    @ResponseBody
    public ResponseEntity<IdentityMatchResponse> resolveIdentity(
            @Valid @RequestBody IdentityMatchRequest request) {
        log.info("API request to resolve identity");
        try {
            IdentityMatchResponse response = 
                identityResolutionService.resolveIdentity(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resolving identity", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * Manual review dashboard
     */
    @GetMapping("/reviews")
    public String reviewDashboard(Model model) {
        log.debug("Loading review dashboard");
        return "review/queue";
    }
}

// ❌ DON'T USE (Pure REST controller without JSP views - not JSHM)
@RestController
@RequestMapping("/api/v1/identities")
public class IdentityController {
    // REST-only endpoints
}
```

### 8. Exception Handling
```java
// ✅ DO USE (Try-catch with logging - JSHM Style)
@Override
protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
    log.debug("Processing batch job");
    
    try {
        // Business logic
        batchJobService.processIdentities();
    } catch (Exception e) {
        log.error("Error in batch job: " + Thread.currentThread().getStackTrace()[1].getMethodName() 
            + "@" + this.getClass().getSimpleName(), e);
        // Handle gracefully
    }
}

// ❌ DON'T USE (Custom exceptions without logging - not JSHM)
public void resolveIdentity() throws IdentityResolutionException {
    // Throw exceptions without logging context
}
```

---

## 🗂️ File Organization Summary

### By Layer:

**Configuration Layer:**
- `SecurityConfig.java` - Spring Security + CAS
- `CacheConfig.java` - Redis configuration
- `OracleDataSourceConfig.java` - Oracle connection pooling
- `WebConfig.java` - Web application config

**Persistence Layer:**
- `IdentityDAO.java` - Entity (traditional with getters/setters)
- `IdentityRepository.java` - Repository interface
- `CommonRepository.java` - Base repository interface
- `CommonRepositoryImpl.java` - Base repository implementation

**Service Layer:**
- `IdentityResolutionService.java` - Main orchestration
- `WaterfallMatchingEngine.java` - Matching logic
- `ApiGatewayService.java` - API ingestion
- `ManualReviewService.java` - Review workflow

**Controller Layer:**
- `IdentityController.java` - API + JSP endpoints
- `ManualReviewController.java` - Review endpoints
- `HomeController.java` - Home page

**Batch/Scheduler:**
- `IreProcessBatch.java` - Batch job (Quartz)

---

## 🔄 Migration from Previous Style

### Changes Required:

1. **Remove Lombok dependencies**
   - Remove `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`
   - Add explicit getters/setters
   - Remove `@Slf4j` and use `LoggerFactory.getLogger()`

2. **Change packaging to WAR**
   - `<packaging>war</packaging>` in pom.xml
   - Add `<scope>provided</scope>` for Tomcat dependencies

3. **Add JSP support**
   - Create `src/main/webapp` directory
   - Add JSP files for admin dashboard and home page
   - Configure JSP view resolver in Spring

4. **Update Spring Boot version**
   - Change from 3.1.5 to 2.1.3
   - Update all dependencies to match 2.1.3 release

5. **Add CAS configuration**
   - Spring Security CAS integration
   - CAS client configuration
   - SAML support

6. **Field injection**
   - Replace constructor injection with `@Autowired` field injection
   - Remove `@RequiredArgsConstructor`

---

## 📦 pom.xml Overview (JSHM-Aligned)

```xml
<groupId>org.hkust</groupId>
<artifactId>ire</artifactId>
<packaging>war</packaging>

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.1.3.RELEASE</version>
</parent>

<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <exclusions>
            <exclusion>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-tomcat</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    
    <!-- Tomcat (Provided) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-tomcat</artifactId>
        <scope>provided</scope>
    </dependency>
    
    <!-- Oracle JDBC -->
    <dependency>
        <groupId>com.oracle.ojdbc</groupId>
        <artifactId>ojdbc10</artifactId>
        <version>19.3.0.0</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- JSP Support -->
    <dependency>
        <groupId>org.apache.tomcat.embed</groupId>
        <artifactId>tomcat-embed-jasper</artifactId>
        <scope>provided</scope>
    </dependency>
    
    <!-- CAS -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-cas</artifactId>
    </dependency>
    
    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- SLF4J (no Lombok) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-logging</artifactId>
    </dependency>
    
    <!-- Quartz -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context-support</artifactId>
    </dependency>
    <dependency>
        <groupId>org.quartz-scheduler</groupId>
        <artifactId>quartz</artifactId>
    </dependency>
    
    <!-- Others (same as JSHM) -->
    <!-- ... -->
</dependencies>
```

---

## ✅ Implementation Checklist

- [ ] Change Spring Boot version to 2.1.3
- [ ] Change packaging to WAR
- [ ] Remove Lombok dependencies
- [ ] Add Tomcat provided dependencies
- [ ] Add JSP support (tomcat-embed-jasper)
- [ ] Add CAS dependencies
- [ ] Rename entities to *DAO pattern
- [ ] Add explicit getters/setters to all DAOs
- [ ] Replace Lombok @Slf4j with LoggerFactory
- [ ] Change all field injections to @Autowired
- [ ] Add detailed Javadoc to all classes
- [ ] Create CommonRepository and CommonRepositoryImpl
- [ ] Create JSP views (index, dashboard, reviews)
- [ ] Add web.xml configuration
- [ ] Add applicationContext-security.xml (CAS config)
- [ ] Create Quartz batch job
- [ ] Update pom.xml (WAR, Spring 2.1.3, dependencies)
- [ ] Add JSP-related controllers
- [ ] Update tests to match new style

---

## 🚀 Timeline for JSHM-Aligned Phase 1

**6-8 weeks** (includes refactoring from previous version)
- Weeks 1-2: Project restructure, WAR setup, JSP views
- Weeks 2-3: DAO classes, repositories, services (JSHM style)
- Weeks 3-4: API Gateway, matching engine, review workflow
- Weeks 4-5: IAM integration, CAS setup, authentication
- Weeks 5-6: Redis caching, batch jobs, monitoring
- Weeks 6-8: Testing, documentation, production ready

---

## 📋 Deliverables (JSHM-Aligned)

✅ Complete Phase 1 codebase aligned with JSHM standards
✅ WAR packaging for Tomcat deployment
✅ JSP admin dashboard
✅ All DAO classes with explicit getters/setters
✅ SLF4J logging throughout
✅ Detailed Javadoc documentation
✅ Custom CommonRepository pattern
✅ CAS authentication integration
✅ Redis caching layer
✅ Quartz batch jobs
✅ Oracle database support
✅ Comprehensive test suite
✅ Production-ready configuration
✅ Deployment documentation

