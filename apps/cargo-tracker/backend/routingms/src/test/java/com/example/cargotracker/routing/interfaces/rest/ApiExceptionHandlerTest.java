package com.example.cargotracker.routing.interfaces.rest;

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
 * 例外と HTTP の対応（architecture_backend.md）。bookingms と同じ形。
 *
 * <p>集約が断ったのか、入力が足りないのか、状態が合わないのかを状態コードで分ける。
 * すべて 500 にすると、利用者は「やり直せばよいのか」「入力を直すのか」を判断できない。</p>
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("集約の業務規則違反は 422")
    void mapsBusinessRuleViolation() {
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("包み",
                        new BusinessRuleViolation("寄港地が繋がっていません")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message")).isEqualTo("寄港地が繋がっていません");
    }

    @Test
    @DisplayName("集約の状態遷移違反は 409")
    void mapsIllegalTransition() {
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("包み",
                        new IllegalTransition("既に登録されています")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("ILLEGAL_STATE");
    }

    @Test
    @DisplayName("包みが 2 枚でも 409 のまま（連鎖の内側まで探す）")
    void findsMarkerThroughNestedWrappers() {
        // 直下の cause だけを読むと、印の付いた文言に届かず 409 が 422 に化ける
        // （IT3 の受け入れテストで実測）。
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("外", new IllegalStateException("中",
                        new IllegalTransition("既に登録されています"))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("原因が無くても文言を返す")
    void handlesMissingCause() {
        ResponseEntity<Map<String, Object>> response =
                handler.onCommandFailed(new CommandExecutionException(null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message")).isEqualTo("処理できませんでした");
    }

    @Test
    @DisplayName("値オブジェクトが投げた業務規則違反は 422")
    void mapsValueObjectViolation() {
        assertThat(handler.onBusinessRuleViolation(new BusinessRuleViolation("船名は必須です"))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("Controller が投げた状態遷移違反も 409")
    void mapsIllegalTransitionFromController() {
        assertThat(handler.onIllegalTransition(new IllegalTransition("競合")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("素の IllegalArgumentException は業務規則違反として受けない")
    void doesNotCatchRawIllegalArgument() {
        // UUID.fromString のようなプログラミングエラーが 422 に化けると、利用者は
        // 直しようのない入力を直そうとし、こちらは不具合に気づけない。
        assertThat(ApiExceptionHandler.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("onBusinessRuleViolation"))
                .allSatisfy(method -> assertThat(method.getParameterTypes()[0])
                        .isEqualTo(BusinessRuleViolation.class));
    }
}
