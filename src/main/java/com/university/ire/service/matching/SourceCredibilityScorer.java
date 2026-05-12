package com.university.ire.service.matching;

import com.university.ire.entity.SourceCredibility;
import com.university.ire.repository.SourceCredibilityRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SourceCredibilityScorer {
    private final SourceCredibilityRepository sourceCredibilityRepository;

    public SourceCredibilityScorer(SourceCredibilityRepository sourceCredibilityRepository) {
        this.sourceCredibilityRepository = sourceCredibilityRepository;
    }

    @Cacheable("source-credibility")
    public double scoreFor(String sourceSystem) {
        return sourceCredibilityRepository.findBySourceSystem(sourceSystem)
                .map(SourceCredibility::getMultiplier)
                .orElseGet(() -> switch (sourceSystem.toUpperCase()) {
                    case "CRM" -> 1.0;
                    case "ATTENDANCE", "ADMS" -> 0.9;
                    default -> 0.7;
                });
    }
}
