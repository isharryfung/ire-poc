package com.university.ire.service.iam;

import com.university.ire.repository.VerifiedIdentityRepository;
import org.springframework.stereotype.Service;

@Service
public class VerifiedIdentityService {

    private final VerifiedIdentityRepository verifiedIdentityRepository;

    public VerifiedIdentityService(VerifiedIdentityRepository verifiedIdentityRepository) {
        this.verifiedIdentityRepository = verifiedIdentityRepository;
    }

    public boolean isVerified(String subject) {
        return verifiedIdentityRepository.findBySubjectAndActiveTrue(subject).isPresent();
    }
}
