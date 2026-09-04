package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.util.Map;
import org.axonframework.messaging.commandhandling.CommandExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 例外と HTTP の対応（architecture_backend.md）。
 *
 * <p>集約が断ったのか、入力が足りないのか、状態が合わないのかを状態コードで
 * 分ける。すべて 500 にすると、利用者は「やり直せばよいのか」「入力を直すのか」を
 * 判断できない。</p>
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("集約の業務規則違反は 422")
    void mapsBusinessRuleViolation() {
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("包み", new IllegalArgumentException("危険物申告が必要です")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message")).isEqualTo("危険物申告が必要です");
    }

    @Test
    @DisplayName("集約の状態遷移違反は 409")
    void mapsIllegalStateFromAggregate() {
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("包み", new IllegalTransition("既に受け付けています")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("ILLEGAL_STATE");
        assertThat(response.getBody().get("message"))
                .as("印は画面に出さない")
                .isEqualTo("既に受け付けています");
    }

    @Test
    @DisplayName("サービス越しで根の型が失われても 409 のまま")
    void keepsConflictWhenCauseTypeIsLost() {
        // サービスを越えた例外は AxonServerRemoteCommandHandlingException に
        // 包まれ、根の型が置き換わる（ADR-0001 決定 5 第 12 項）。型で見分けると、
        // コマンドがサービスを越えた瞬間に 409 が黙って 422 に劣化する。
        Throwable lostType = new RuntimeException(
                IllegalTransition.MARKER + "既に受け付けています");

        ResponseEntity<Map<String, Object>> response =
                handler.onCommandFailed(new CommandExecutionException("包み", lostType));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message")).isEqualTo("既に受け付けています");
    }

    @Test
    @DisplayName("業務規則違反の印も画面には出さない")
    void stripsBusinessRuleMarker() {
        ResponseEntity<Map<String, Object>> response = handler.onBusinessRuleViolation(
                new BusinessRuleViolation("割引率は 0.0000〜0.3000 の範囲です"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message"))
                .isEqualTo("割引率は 0.0000〜0.3000 の範囲です");
    }

    @Test
    @DisplayName("原因が無くても文言を返す")
    void survivesMissingCause() {
        // サービス越しでは根の例外が置き換わり、原因が取れないことがある。
        // ここで NPE を出すと、断った理由の代わりに 500 が返る。
        assertThat(handler.onCommandFailed(new CommandExecutionException("外側の文言", null))
                .getBody().get("message")).isEqualTo("外側の文言");
        assertThat(handler.onCommandFailed(new CommandExecutionException(null, null))
                .getBody().get("message")).isEqualTo("処理できませんでした");
    }

    @Test
    @DisplayName("Controller が投げた状態遷移違反も 409")
    void mapsIllegalTransition() {
        assertThat(handler.onIllegalTransition(new IllegalTransition("競合")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("値オブジェクトが投げた業務規則違反は 422")
    void mapsIllegalArgument() {
        assertThat(handler.onBusinessRuleViolation(new IllegalArgumentException("範囲外"))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
