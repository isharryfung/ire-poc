package com.university.ire.repository;

import com.university.ire.entity.VerifiedIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerifiedIdentityRepository extends JpaRepository<VerifiedIdentity, Long> {
    Optional<VerifiedIdentity> findBySubjectAndActiveTrue(String subject);
}
