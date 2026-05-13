package com.example.cargotracker.authms.interfaces.rest;

import com.example.cargotracker.authms.application.AdminUserCommandService;
import com.example.cargotracker.authms.application.AdminUserQueryService;
import com.example.cargotracker.authms.application.AuthCommandService;
import com.example.cargotracker.authms.domain.model.Role;
import com.example.cargotracker.authms.interfaces.rest.dto.RegisterRequest;
import com.example.cargotracker.authms.interfaces.rest.dto.UpdateRolesRequest;
import com.example.cargotracker.authms.interfaces.rest.dto.UpdateStatusRequest;
import com.example.cargotracker.authms.interfaces.rest.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "ユーザー管理 API（管理者専用）")
public class AdminUserController {

    private static final String MESSAGE_KEY = "message";

    private final AdminUserQueryService queryService;
    private final AdminUserCommandService commandService;
    private final AuthCommandService authCommandService;

    public AdminUserController(AdminUserQueryService queryService,
                               AdminUserCommandService commandService,
                               AuthCommandService authCommandService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.authCommandService = authCommandService;
    }

    @GetMapping
    @Operation(summary = "全ユーザー一覧取得")
    public ResponseEntity<List<UserSummary>> listUsers() {
        return ResponseEntity.ok(queryService.findAllUsers());
    }

    @PostMapping
    @Operation(summary = "新規ユーザー作成")
    public ResponseEntity<Object> createUser(@Valid @RequestBody RegisterRequest request) {
        try {
            Role role = request.role() != null ? Role.valueOf(request.role()) : Role.ROLE_SHIPPER;
            authCommandService.register(request.username(), request.email(), request.password(), role);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("username", request.username(), "email", request.email()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "ロール付与・剥奪")
    public ResponseEntity<Object> updateRoles(@PathVariable String id,
                                              @Valid @RequestBody UpdateRolesRequest request) {
        try {
            commandService.updateRoles(id, request.roles());
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "ロールを更新しました"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "有効化・無効化")
    public ResponseEntity<Object> updateStatus(@PathVariable String id,
                                               @RequestBody UpdateStatusRequest request) {
        try {
            commandService.updateStatus(id, request.enabled());
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "ステータスを更新しました"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
    }
}
