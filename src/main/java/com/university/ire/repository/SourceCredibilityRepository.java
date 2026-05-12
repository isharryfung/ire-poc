package com.university.ire.repository;

import com.university.ire.entity.SourceCredibility;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceCredibilityRepository extends JpaRepository<SourceCredibility, Long> {
    Optional<SourceCredibility> findBySourceSystem(String sourceSystem);
}
