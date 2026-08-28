package com.example.authms.interfaces.rest;

import com.example.authms.application.internal.commandservices.LoginResult;
import com.example.authms.application.internal.commandservices.LoginUseCase;
import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.domain.model.AuthEventType;
import com.example.shared.auth.AnyAuthenticatedUser;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request) {
        Optional<LoginResult> result = loginUseCase.login(request.userId(), request.password());

        return result
                .<ResponseEntity<AuthenticationResponse>>map(login -> ResponseEntity.ok(new LoginResponse(
                        login.token(),
                        login.userId(),
                        login.displayName(),
                        login.roles().stream().map(Role::name).sorted().toList())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(FAILURE_MESSAGE)));
    }

    /**
     * 離席する。
     *
     * <p><strong>ロールを問わない。</strong>自分の離席を記録するだけで、他人の何かを
     * 動かさない。ロールで絞ると、ロールを 1 つも持たない利用者が離席できなくなる。
     */
    @AnyAuthenticatedUser
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId) {
        // トークンは自己完結型のため、サーバー側に破棄する状態はない。
        // それでも「いつ誰が明示的に離席したか」は監査上の手がかりになるため残す。
        auditLogger.log(userId, AuthEventType.LOGOUT, null);
        return ResponseEntity.noContent().build();
    }
}
