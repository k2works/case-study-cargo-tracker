package com.example.cargotracker.authms.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateRolesRequest(
        @NotNull(message = "roles は必須です")
        List<String> roles
) {}
