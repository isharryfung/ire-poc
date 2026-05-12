package com.university.ire.dto;

import java.util.Set;

public record IamUserDto(
        String subject,
        String username,
        Set<String> roles,
        boolean verified
) {}
