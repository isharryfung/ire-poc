-- Flyway Migration: Create IRE Database Schema (H2)
-- Version: 1.0.0
-- Description: Create all core tables for Identity Resolution Engine
-- Author: IRE Team
-- Created: 2026-05-13

-- ============================================================================
-- TABLE: identities
-- Purpose: Golden identity records - single source of truth for each person
-- ============================================================================
CREATE TABLE IF NOT EXISTS identities (
    golden_id VARCHAR(50) PRIMARY KEY,
    hkid VARCHAR(20) UNIQUE,
    staff_id VARCHAR(20) UNIQUE,
    student_id VARCHAR(20) UNIQUE,
    alumni_id VARCHAR(20) UNIQUE,
    email VARCHAR(100) UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    middle_name VARCHAR(100),
    date_of_birth DATE,
    phone_number VARCHAR(20),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_identities_hkid ON identities(hkid);
CREATE INDEX idx_identities_email ON identities(email);
CREATE INDEX idx_identities_staff_id ON identities(staff_id);
CREATE INDEX idx_identities_student_id ON identities(student_id);
CREATE INDEX idx_identities_alumni_id ON identities(alumni_id);
CREATE INDEX idx_identities_status ON identities(status);

-- ============================================================================
-- TABLE: identity_links
-- Purpose: Links source system records to golden identities (many-to-one)
-- ============================================================================
CREATE TABLE IF NOT EXISTS identity_links (
    link_id VARCHAR(50) PRIMARY KEY,
    golden_id VARCHAR(50) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    source_email VARCHAR(100),
    source_hkid VARCHAR(20),
    match_confidence DOUBLE,
    match_tier VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    FOREIGN KEY (golden_id) REFERENCES identities(golden_id) ON DELETE CASCADE,
    UNIQUE KEY uk_identity_links (source_system, source_id)
);

CREATE INDEX idx_identity_links_golden_id ON identity_links(golden_id);
CREATE INDEX idx_identity_links_source_system ON identity_links(source_system);
CREATE INDEX idx_identity_links_match_tier ON identity_links(match_tier);

-- ============================================================================
-- TABLE: source_credibility
-- Purpose: Credibility scores for each source system (trust multipliers)
-- ============================================================================
CREATE TABLE IF NOT EXISTS source_credibility (
    source_system VARCHAR(50) PRIMARY KEY,
    credibility_score DOUBLE,
    description VARCHAR(200),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50)
);

-- ============================================================================
-- TABLE: manual_reviews
-- Purpose: Identity records requiring human review (Tier-3 cases)
-- ============================================================================
CREATE TABLE IF NOT EXISTS manual_reviews (
    review_id VARCHAR(50) PRIMARY KEY,
    source_system VARCHAR(50),
    source_id VARCHAR(100),
    canonical_identity_json CLOB,
    confidence_score DOUBLE,
    match_tier VARCHAR(20),
    potential_matches_json CLOB,
    status VARCHAR(20),
    assigned_to VARCHAR(50),
    review_notes CLOB,
    decision VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    created_by VARCHAR(50)
);

CREATE INDEX idx_manual_reviews_status ON manual_reviews(status);
CREATE INDEX idx_manual_reviews_source_system ON manual_reviews(source_system);
CREATE INDEX idx_manual_reviews_assigned_to ON manual_reviews(assigned_to);
CREATE INDEX idx_manual_reviews_created_at ON manual_reviews(created_at);

-- ============================================================================
-- TABLE: audit_logs
-- Purpose: Complete audit trail of all identity operations
-- ============================================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id VARCHAR(50) PRIMARY KEY,
    action VARCHAR(50),
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    source_system VARCHAR(50),
    old_values_json CLOB,
    new_values_json CLOB,
    details VARCHAR(1000),
    status VARCHAR(20),
    error_message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50)
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_source_system ON audit_logs(source_system);

-- ============================================================================
-- TABLE: confidence_thresholds
-- Purpose: Configuration for matching confidence thresholds
-- ============================================================================
CREATE TABLE IF NOT EXISTS confidence_thresholds (
    threshold_id VARCHAR(50) PRIMARY KEY,
    threshold_type VARCHAR(50),
    threshold_value DOUBLE,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- SEED DATA: Source Credibility
-- Credibility weights for different source systems
-- ============================================================================
INSERT INTO source_credibility (source_system, credibility_score, description) VALUES
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
INSERT INTO confidence_thresholds (threshold_id, threshold_type, threshold_value, description) VALUES
('TIER2_THRESHOLD', 'TIER2_MINIMUM', 0.70, 'Minimum confidence for Tier-2 matching (70%)'),
('AUTO_MERGE_THRESHOLD', 'AUTO_MERGE', 0.85, 'Minimum confidence for automatic merge (85%)'),
('MANUAL_REVIEW_MIN', 'MANUAL_REVIEW', 0.50, 'Minimum confidence for manual review (50%)'),
('MANUAL_REVIEW_MAX', 'MANUAL_REVIEW_UPPER', 0.85, 'Maximum confidence for manual review (85%)');

-- ============================================================================
-- SEED DATA: Sample Identity
-- Test identity for verification
-- ============================================================================
INSERT INTO identities (golden_id, hkid, staff_id, email, first_name, last_name, status, created_by) VALUES
('GID-001', 'A123456(7)', 'STF000001', 'john.doe@ust.hk', 'John', 'Doe', 'ACTIVE', 'SYSTEM');

-- ============================================================================
-- SEED DATA: Sample Identity Links
-- ============================================================================
INSERT INTO identity_links (link_id, golden_id, source_system, source_id, match_confidence, match_tier, created_by) VALUES
('LINK-001', 'GID-001', 'ADMS', 'ADMS-20150001', 0.95, 'TIER_1', 'SYSTEM'),
('LINK-002', 'GID-001', 'CRM', 'CRM-00001', 1.0, 'TIER_1', 'SYSTEM');

-- ============================================================================
-- VERIFICATION QUERIES
-- Uncomment to verify schema creation
-- ============================================================================
-- SELECT COUNT(*) as identity_count FROM identities;
-- SELECT COUNT(*) as link_count FROM identity_links;
-- SELECT COUNT(*) as credibility_count FROM source_credibility;
-- SELECT source_system, credibility_score FROM source_credibility ORDER BY credibility_score DESC;
-- SELECT * FROM identities WHERE golden_id = 'GID-001';
