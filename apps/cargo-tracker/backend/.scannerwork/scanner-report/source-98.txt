package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.interfaces.rest.ShipperController.DuplicateShipperEmailException;
import java.util.Map;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import org.axonframework.messaging.commandhandling.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API のエラー対応表（architecture_backend.md「例外と HTTP の対応」）。
 *
 * <p>状態遷移違反は 409、業務規則違反は 422、投影に未反映は 202 と分ける。
 * どれも 500 にすると、利用者は「やり直せばよいのか」「入力が悪いのか」を判断できない。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String CODE = "code";
    private static final String MESSAGE = "message";


    @ExceptionHandler(DuplicateShipperEmailException.class)
    public ResponseEntity<Map<String, Object>> onDuplicateEmail(DuplicateShipperEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(CODE, "SHIPPER_EMAIL_DUPLICATE", MESSAGE, e.getMessage()));
    }

    /**
     * 値オブジェクトと集約が弾いた業務規則違反。
     *
     * <p><b>{@code IllegalArgumentException} を広く受けない。</b> 広く受けると
     * {@code UUID.fromString} のようなプログラミングエラーまで業務規則違反に化け、
     * 画面には「入力が正しくありません」と出る。利用者は直しようのない入力を
     * 直そうとし、こちらは不具合に気づけない。ドメイン層は
     * {@link BusinessRuleViolation} だけを投げる（規約テストで固定）。</p>
     */
    @ExceptionHandler(BusinessRuleViolation.class)
    public ResponseEntity<Map<String, Object>> onBusinessRuleViolation(BusinessRuleViolation e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(CODE, "BUSINESS_RULE_VIOLATION", MESSAGE,
                        BusinessRuleViolation.strip(e.getMessage())));
    }

    /**
     * 集約が断った業務規則違反。
     *
     * <p>集約の中で投げた例外は {@code CommandExecutionException} に包まれ、
     * サービス越しでは根の型まで {@code AxonServerRemoteCommandHandlingException} に
     * 置き換わる。包みを解かないと <b>500 になる</b>。断ったのは業務の判断なので、
     * 画面には「壊れた」ではなく理由が出なければならない。</p>
     *
     * <p>状態遷移違反（{@code IllegalStateException}）は 409、それ以外の業務規則は
     * 422（architecture_backend.md「例外と HTTP の対応」）。</p>
     */
    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<Map<String, Object>> onCommandFailed(CommandExecutionException e) {
        String message = deepestMessage(e);
        // **型で見ない。** サービス越しに来た例外は根の型が置き換わるので、
        // instanceof で分けるとコマンドがサービスを越えた瞬間に 409 が 422 に
        // 劣化する（ADR-0001 決定 5 第 12 項）。種類は文言の印で運ぶ。
        //
        // **連鎖のいちばん外側だけを見ない。** 包みは 2 枚以上になることがあり、
        // 直下の cause だけを読むと印が付いた文言に届かず、409 が 422 に化ける
        // （IT3 の受け入れテストで実測。集約の単体テストでは判別できない）。
        if (containsMarker(e, IllegalTransition.MARKER)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(CODE, "ILLEGAL_STATE", MESSAGE,
                            BusinessRuleViolation.strip(message)));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(CODE, "BUSINESS_RULE_VIOLATION", MESSAGE,
                        BusinessRuleViolation.strip(message)));
    }

    /** 連鎖のどこかに印があるか。包みの枚数に依存しない。 */
    private static boolean containsMarker(Throwable throwable, String marker) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** 連鎖のいちばん内側の文言。無ければ外側から順に探す。 */
    private static String deepestMessage(Throwable throwable) {
        String message = null;
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                message = t.getMessage();
            }
        }
        return message == null ? "処理できませんでした" : message;
    }

    /** 状態遷移違反。集約の外（Controller）で判断したもの。 */
    @ExceptionHandler(IllegalTransition.class)
    public ResponseEntity<Map<String, Object>> onIllegalTransition(IllegalTransition e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(CODE, "ILLEGAL_STATE", MESSAGE,
                        BusinessRuleViolation.strip(e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onInvalidRequest(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("入力が正しくありません");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(CODE, "INVALID_REQUEST", MESSAGE, message));
    }
}
