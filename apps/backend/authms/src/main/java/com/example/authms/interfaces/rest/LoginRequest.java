package com.example.authms.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String userId, @NotBlank String password) {
}
