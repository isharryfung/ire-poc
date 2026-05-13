-- Flyway Migration: Create IRE Database Schema (H2)
-- Version: 1.0.0
-- Description: Create all core tables for Identity Resolution Engine
-- Author: IRE Team
-- Created: 2026-05-13
-- 
-- CRITICAL: Column names MUST match DAO @Column mappings exactly (case-sensitive)
-- Hibernate will fail to find columns if names don't match!

-- ============================================================================
-- TABLE: identities
-- Purpose: Golden identity records - single source of truth for each person
-- CRITICAL: Column names must match IdentityDAO @Column annotations
-- ============================================================================
CREATE TABLE IF NOT EXISTS identities (
    ID BIGINT IDENTITY PRIMARY KEY,
    GOLDEN_ID VARCHAR(50) NOT NULL UNIQUE,
    HKID VARCHAR(20) UNIQUE,
    STAFF_ID VARCHAR(20) UNIQUE,
    STUDENT_ID VARCHAR(20) UNIQUE,
    ALUMNI_ID VARCHAR(20) UNIQUE,
    EMAIL VARCHAR(100) NOT NULL UNIQUE,
    FIRST_NAME VARCHAR(100),
    LAST_NAME VARCHAR(100),
    MIDDLE_NAME VARCHAR(100),
    DATE_OF_BIRTH DATE,
    PHONE VARCHAR(20),
    PHONE_NUMBER VARCHAR(20),
    STATUS VARCHAR(20),
    CONFIDENCE_SCORE DOUBLE,
    PRIMARY_SOURCE VARCHAR(100),
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY VARCHAR(50),
    CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_BY VARCHAR(50),
    UPDATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_identities_hkid ON identities(HKID);
CREATE INDEX idx_identities_email ON identities(EMAIL);
CREATE INDEX idx_identities_staff_id ON identities(STAFF_ID);
CREATE INDEX idx_identities_student_id ON identities(STUDENT_ID);
CREATE INDEX idx_identities_alumni_id ON identities(ALUMNI_ID);
CREATE INDEX idx_identities_status ON identities(STATUS);

-- ============================================================================
-- TABLE: identity_links
-- Purpose: Links source system records to golden identities (many-to-one)
-- ============================================================================
CREATE TABLE IF NOT EXISTS identity_links (
    LINK_ID VARCHAR(50) PRIMARY KEY,
    GOLDEN_ID VARCHAR(50) NOT NULL,
    SOURCE_SYSTEM VARCHAR(50) NOT NULL,
    SOURCE_ID VARCHAR(100) NOT NULL,
    SOURCE_EMAIL VARCHAR(100),
    SOURCE_HKID VARCHAR(20),
    MATCH_CONFIDENCE DOUBLE,
    MATCH_TIER VARCHAR(20),
    STATUS VARCHAR(20),
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY VARCHAR(50),
    FOREIGN KEY (GOLDEN_ID) REFERENCES identities(GOLDEN_ID) ON DELETE CASCADE,
    UNIQUE KEY uk_identity_links (SOURCE_SYSTEM, SOURCE_ID)
);

CREATE INDEX idx_identity_links_golden_id ON identity_links(GOLDEN_ID);
CREATE INDEX idx_identity_links_source_system ON identity_links(SOURCE_SYSTEM);
CREATE INDEX idx_identity_links_match_tier ON identity_links(MATCH_TIER);

-- ============================================================================
-- TABLE: source_credibility
-- Purpose: Credibility scores for each source system (trust multipliers)
-- ============================================================================
CREATE TABLE IF NOT EXISTS source_credibility (
    SOURCE_SYSTEM VARCHAR(50) PRIMARY KEY,
    CREDIBILITY_SCORE DOUBLE,
    DESCRIPTION VARCHAR(200),
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_BY VARCHAR(50)
);

-- ============================================================================
-- TABLE: manual_reviews
-- Purpose: Identity records requiring human review (Tier-3 cases)
-- ============================================================================
CREATE TABLE IF NOT EXISTS manual_reviews (
    REVIEW_ID VARCHAR(50) PRIMARY KEY,
    SOURCE_SYSTEM VARCHAR(50),
    SOURCE_ID VARCHAR(100),
    CANONICAL_IDENTITY_JSON CLOB,
    CONFIDENCE_SCORE DOUBLE,
    MATCH_TIER VARCHAR(20),
    POTENTIAL_MATCHES_JSON CLOB,
    STATUS VARCHAR(20),
    ASSIGNED_TO VARCHAR(50),
    REVIEW_NOTES CLOB,
    DECISION VARCHAR(20),
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    REVIEWED_AT TIMESTAMP,
    CREATED_BY VARCHAR(50)
);

CREATE INDEX idx_manual_reviews_status ON manual_reviews(STATUS);
CREATE INDEX idx_manual_reviews_source_system ON manual_reviews(SOURCE_SYSTEM);
CREATE INDEX idx_manual_reviews_assigned_to ON manual_reviews(ASSIGNED_TO);
CREATE INDEX idx_manual_reviews_created_at ON manual_reviews(CREATED_AT);

-- ============================================================================
-- TABLE: audit_logs
-- Purpose: Complete audit trail of all identity operations
-- ============================================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    AUDIT_ID VARCHAR(50) PRIMARY KEY,
    ACTION VARCHAR(50),
    ENTITY_TYPE VARCHAR(50),
    ENTITY_ID VARCHAR(100),
    SOURCE_SYSTEM VARCHAR(50),
    OLD_VALUES_JSON CLOB,
    NEW_VALUES_JSON CLOB,
    DETAILS VARCHAR(1000),
    STATUS VARCHAR(20),
    ERROR_MESSAGE VARCHAR(500),
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY VARCHAR(50)
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(ENTITY_TYPE, ENTITY_ID);
CREATE INDEX idx_audit_logs_action ON audit_logs(ACTION);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(CREATED_AT);
CREATE INDEX idx_audit_logs_source_system ON audit_logs(SOURCE_SYSTEM);

-- ============================================================================
-- TABLE: confidence_thresholds
-- Purpose: Configuration for matching confidence thresholds
-- ============================================================================
CREATE TABLE IF NOT EXISTS confidence_thresholds (
    THRESHOLD_ID VARCHAR(50) PRIMARY KEY,
    THRESHOLD_TYPE VARCHAR(50),
    THRESHOLD_VALUE DOUBLE,
    DESCRIPTION VARCHAR(200),
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- TABLE: verified_identities
-- Purpose: Identities verified by IAM (Midpoint integration)
-- ============================================================================
CREATE TABLE IF NOT EXISTS verified_identities (
    ID BIGINT IDENTITY PRIMARY KEY,
    GOLDEN_ID VARCHAR(50) NOT NULL,
    IAM_ID VARCHAR(100),
    ITSC_ACCOUNT VARCHAR(50),
    ROLES VARCHAR(1000),
    VERIFIED_STATUS VARCHAR(20),
    LAST_SYNC_DATE TIMESTAMP,
    CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (GOLDEN_ID) REFERENCES identities(GOLDEN_ID) ON DELETE CASCADE
);

CREATE INDEX idx_verified_identities_golden_id ON verified_identities(GOLDEN_ID);
CREATE INDEX idx_verified_identities_iam_id ON verified_identities(IAM_ID);

-- ============================================================================
-- SEED DATA: Source Credibility
-- Credibility weights for different source systems
-- ============================================================================
INSERT INTO source_credibility (SOURCE_SYSTEM, CREDIBILITY_SCORE, DESCRIPTION) VALUES
('CRM', 1.0, 'Master data system - highest credibility (100% trusted)'),
('ADMS', 0.9, 'Admissions system - high credibility (90% trusted)'),
('ATTENDANCE', 0.9, 'Attendance system - high credibility (90% trusted)'),
('EVENT_SYSTEM', 0.9, 'Event management system - high credibility (90% trusted)'),
('IAM', 1.0, 'Identity & Access Management - highest credibility (100% trusted)'),
('THIRD_PARTY', 0.8, 'Third-party forms and sources - medium credibility (80% trusted)'),
('GOOGLE_FORMS', 0.8, 'Google Forms submissions - medium credibility (80% trusted)');

-- ============================================================================
-- SEED DATA: Confidence Thresholds
-- Threshold values for Tier-2 auto-merge and manual review routing
-- ============================================================================
INSERT INTO confidence_thresholds (THRESHOLD_ID, THRESHOLD_TYPE, THRESHOLD_VALUE, DESCRIPTION) VALUES
('TIER2_THRESHOLD', 'TIER2_MINIMUM', 0.70, 'Minimum confidence for Tier-2 matching (70%)'),
('AUTO_MERGE_THRESHOLD', 'AUTO_MERGE', 0.85, 'Minimum confidence for automatic merge (85%)'),
('MANUAL_REVIEW_MIN', 'MANUAL_REVIEW', 0.50, 'Minimum confidence for manual review (50%)'),
('MANUAL_REVIEW_MAX', 'MANUAL_REVIEW_UPPER', 0.85, 'Maximum confidence for manual review (85%)');

-- ============================================================================
-- SEED DATA: Sample Identity
-- Test identity for verification
-- ============================================================================
INSERT INTO identities (GOLDEN_ID, HKID, STAFF_ID, EMAIL, FIRST_NAME, LAST_NAME, STATUS, CREATED_BY) VALUES
('GID-001', 'A123456(7)', 'STF000001', 'john.doe@ust.hk', 'John', 'Doe', 'ACTIVE', 'SYSTEM');

-- ============================================================================
-- SEED DATA: Sample Identity Links
-- ============================================================================
INSERT INTO identity_links (LINK_ID, GOLDEN_ID, SOURCE_SYSTEM, SOURCE_ID, MATCH_CONFIDENCE, MATCH_TIER, CREATED_BY) VALUES
('LINK-001', 'GID-001', 'ADMS', 'ADMS-20150001', 0.95, 'TIER_1', 'SYSTEM'),
('LINK-002', 'GID-001', 'CRM', 'CRM-00001', 1.0, 'TIER_1', 'SYSTEM');

-- ============================================================================
-- VERIFICATION QUERIES
-- Uncomment to verify schema creation
-- ============================================================================
-- SELECT COUNT(*) as identity_count FROM identities;
-- SELECT COUNT(*) as link_count FROM identity_links;
-- SELECT COUNT(*) as credibility_count FROM source_credibility;
-- SELECT SOURCE_SYSTEM, CREDIBILITY_SCORE FROM source_credibility ORDER BY CREDIBILITY_SCORE DESC;
-- SELECT * FROM identities WHERE GOLDEN_ID = 'GID-001';
