package com.university.ire.dto;

public record ApiGatewayResponse(
        String identityId,
        String action,
        String tier,
        double confidence,
        String reviewId,
        String message
) {}
