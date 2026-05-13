package com.example.cargotracker.authms.interfaces.rest;

import com.example.cargotracker.authms.application.AuthCommandService;
import com.example.cargotracker.authms.application.AuthQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "認証 API")
public class AuthController {

    private final AuthCommandService commandService;
    private final AuthQueryService queryService;

    public AuthController(AuthCommandService commandService, AuthQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "ユーザー登録")
    public void register(@RequestBody RegisterRequest request) {
        commandService.register(request.username(), request.email(), request.password());
    }

    @PostMapping("/login")
    @Operation(summary = "ログイン・JWT 取得")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        String token = queryService.login(request.username(), request.password());
        return Map.of("token", token);
    }

    public record RegisterRequest(String username, String email, String password) {}
    public record LoginRequest(String username, String password) {}
}
