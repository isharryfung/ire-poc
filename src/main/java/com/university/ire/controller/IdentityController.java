package com.university.ire.controller;

import com.university.ire.entity.Identity;
import com.university.ire.repository.IdentityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identities")
public class IdentityController {

    private final IdentityRepository identityRepository;

    public IdentityController(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Identity> get(@PathVariable Long id) {
        return identityRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
