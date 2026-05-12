package com.university.ire.service.identity;

import com.university.ire.dto.CanonicalIdentity;
import com.university.ire.entity.Identity;
import org.springframework.stereotype.Service;

@Service
public class IdentityMergeService {

    public Identity mergeInto(Identity target, CanonicalIdentity incoming) {
        if (isBlank(target.getHkid())) target.setHkid(incoming.getHkid());
        if (isBlank(target.getStaffId())) target.setStaffId(incoming.getStaffId());
        if (isBlank(target.getStudentId())) target.setStudentId(incoming.getStudentId());
        if (isBlank(target.getBadgeId())) target.setBadgeId(incoming.getBadgeId());
        if (isBlank(target.getEmail())) target.setEmail(incoming.getEmail());
        if (isBlank(target.getPhone())) target.setPhone(incoming.getPhone());
        if (isBlank(target.getFirstName())) target.setFirstName(incoming.getFirstName());
        if (isBlank(target.getLastName())) target.setLastName(incoming.getLastName());
        return target;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
