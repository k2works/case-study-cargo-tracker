package com.example.cargotracker.authms.interfaces.rest.dto;

import java.util.List;

public record UserSummary(String id, String username, String email, boolean enabled, List<String> roles) {}
