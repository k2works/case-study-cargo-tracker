package com.example.authms.interfaces.rest.dto;

import java.util.List;

public record TokenResponse(
        String token,
        String username,
        List<String> roles
) {}
