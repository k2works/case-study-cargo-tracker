package com.example.authms.interfaces.rest;

public record ErrorResponse(String message) implements AuthenticationResponse {
}
