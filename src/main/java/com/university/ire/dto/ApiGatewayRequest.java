package com.university.ire.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ApiGatewayRequest(
        @NotBlank String sourceSystem,
        @NotBlank String sourceRecordId,
        @NotNull Instant timestamp,
        @NotNull JsonNode payload
) {}
