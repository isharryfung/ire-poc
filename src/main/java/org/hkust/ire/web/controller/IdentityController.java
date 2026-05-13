package org.hkust.ire.web.controller;

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.hkust.ire.db.persistence.repository.IdentityRepository;
import org.hkust.ire.db.persistence.service.identity.IdentityCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST controller for identity lookup endpoints.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Controller
@RequestMapping("/api/v1/identities")
public class IdentityController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private IdentityCacheService identityCacheService;

    /**
     * Retrieves an identity by golden ID, checking cache first.
     *
     * @param goldenId the golden identity ID
     * @return IdentityDAO or 404
     */
    @GetMapping("/{goldenId}")
    @ResponseBody
    public ResponseEntity<IdentityDAO> getByGoldenId(@PathVariable String goldenId) {
        log.debug("Fetching identity: goldenId={}", goldenId);
        try {
            IdentityDAO cached = identityCacheService.getCachedIdentity(goldenId);
            if (cached != null) {
                return ResponseEntity.ok(cached);
            }
            Optional<IdentityDAO> opt = identityRepository.findByGoldenId(goldenId);
            if (opt.isPresent()) {
                identityCacheService.cacheIdentity(goldenId, opt.get());
                return ResponseEntity.ok(opt.get());
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching identity {}: {}", goldenId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves an identity by email address.
     *
     * @param email the email address
     * @return IdentityDAO or 404
     */
    @GetMapping("/by-email")
    @ResponseBody
    public ResponseEntity<IdentityDAO> getByEmail(@RequestParam String email) {
        log.debug("Fetching identity by email");
        try {
            Optional<IdentityDAO> opt = identityRepository.findByEmail(email);
            return opt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching identity by email: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
