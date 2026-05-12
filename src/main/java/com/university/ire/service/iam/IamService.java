package com.university.ire.service.iam;

import com.university.ire.dto.IamUserDto;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class IamService {

    private final VerifiedIdentityService verifiedIdentityService;

    public IamService(VerifiedIdentityService verifiedIdentityService) {
        this.verifiedIdentityService = verifiedIdentityService;
    }

    public IamUserDto fromJwt(Jwt jwt) {
        String subject = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        Set<String> roles = jwt.getClaimAsStringList("roles") == null
                ? Set.of("READER")
                : Set.copyOf(jwt.getClaimAsStringList("roles"));
        return new IamUserDto(subject, username == null ? subject : username, roles, verifiedIdentityService.isVerified(subject));
    }
}
