package com.university.ire.service.identity;

import com.university.ire.entity.Identity;
import com.university.ire.repository.IdentityRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class IdentityCacheService {

    private final IdentityRepository identityRepository;

    public IdentityCacheService(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    @Cacheable(value = "identity-by-id", key = "#id")
    public Identity findById(Long id) {
        return identityRepository.findById(id).orElse(null);
    }

    @CacheEvict(value = "identity-by-id", key = "#identity.id")
    public Identity save(Identity identity) {
        return identityRepository.save(identity);
    }
}
