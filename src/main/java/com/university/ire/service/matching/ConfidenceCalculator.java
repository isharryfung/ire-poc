package com.university.ire.service.matching;

import org.springframework.stereotype.Component;

@Component
public class ConfidenceCalculator {

    public double composite(double baseMatchScore, double sourceCredibility) {
        return Math.max(0, Math.min(1, baseMatchScore * sourceCredibility));
    }
}
