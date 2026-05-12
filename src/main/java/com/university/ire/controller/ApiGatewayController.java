package com.university.ire.controller;

import com.university.ire.dto.ApiGatewayRequest;
import com.university.ire.dto.ApiGatewayResponse;
import com.university.ire.service.gateway.ApiGatewayService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApiGatewayController {

    private final ApiGatewayService apiGatewayService;

    public ApiGatewayController(ApiGatewayService apiGatewayService) {
        this.apiGatewayService = apiGatewayService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<ApiGatewayResponse> ingest(@Valid @RequestBody ApiGatewayRequest request) {
        return ResponseEntity.ok(apiGatewayService.ingest(request));
    }

    @GetMapping("/ingest/status")
    public Map<String, String> status() {
        return Map.of("status", "UP");
    }
}
