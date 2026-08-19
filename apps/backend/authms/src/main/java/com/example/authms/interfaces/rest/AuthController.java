package com.example.authms.interfaces.rest;

import com.example.authms.application.internal.LoginResult;
import com.example.authms.application.internal.LoginUseCase;
import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.domain.model.AuthEventType;
import com.example.shared.auth.Role;
import com.example.shared.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * ログイン失敗時に返す唯一の文言。
     *
     * <p>認証情報誤り・ロック中・無効化を区別して返すと、「その利用者 ID は存在する」ことを
     * 攻撃者に教えてしまう（US31）。何が起きたかは監査ログにだけ残す。
     */
    private static final String FAILURE_MESSAGE = "利用者 ID またはパスワードが正しくありません";

    private final LoginUseCase loginUseCase;
    private final AuthAuditLogger auditLogger;

    public AuthController(LoginUseCase loginUseCase, AuthAuditLogger auditLogger) {
        this.loginUseCase = loginUseCase;
        this.auditLogger = auditLogger;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<LoginResult> result = loginUseCase.login(request.userId(), request.password());

        return result
                .<ResponseEntity<?>>map(login -> ResponseEntity.ok(new LoginResponse(
                        login.token(),
                        login.userId(),
                        login.displayName(),
                        login.roles().stream().map(Role::name).sorted().toList())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(FAILURE_MESSAGE)));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        List<String> roleNames = roles == null || roles.isBlank() ? List.of() : List.of(roles.split(","));
        return ResponseEntity.ok(new LoginResponse(null, userId, userId, roleNames));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId) {
        // トークンは自己完結型のため、サーバー側に破棄する状態はない。
        // それでも「いつ誰が明示的に離席したか」は監査上の手がかりになるため残す。
        auditLogger.record(userId, AuthEventType.LOGOUT, null);
        return ResponseEntity.noContent().build();
    }
}
