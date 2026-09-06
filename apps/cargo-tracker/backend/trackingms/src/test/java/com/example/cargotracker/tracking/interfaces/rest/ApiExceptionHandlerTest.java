package com.example.cargotracker.tracking.interfaces.rest;

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
 * <p><b>trackingms は連鎖の受け手</b>なので、断り方が bookingms と違うと、送った側が
 * 「壊れた」と「断られた」を区別できない。3 サービスで同じ形にする
 * （{@code EventSourcedServicesHaveTheSameShapeTest} が形の有無を見ている）。</p>
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("集約の業務規則違反は 422")
    void mapsBusinessRuleViolation() {
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("包み", new BusinessRuleViolation("旅程は必須です")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message")).isEqualTo("旅程は必須です");
    }

    @Test
    @DisplayName("集約の状態遷移違反は 409")
    void mapsIllegalTransition() {
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("包み",
                        new IllegalTransition("追跡 T-1 は既に開始しています")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message")).isEqualTo("追跡 T-1 は既に開始しています");
    }

    @Test
    @DisplayName("包みが 2 枚以上でも 409 が 422 に化けない")
    void looksThroughEveryWrapper() {
        // 連鎖のいちばん外側だけを見ると、印が付いた文言に届かない（IT3 で実測）。
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException("外", new IllegalStateException("中",
                        new IllegalTransition("追跡 T-1 は既に開始しています"))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("遠隔から来た文言でも、断った理由と例外クラス名を取り違えない")
    void prefersTheMarkedMessage() {
        // **器だけの文言が最深に来る。** いちばん内側を採ると理由が届かない
        // （IT7 のクラスタで実測）。印より前（例外クラスの完全名）も切り落とす。
        ResponseEntity<Map<String, Object>> response = handler.onCommandFailed(
                new CommandExecutionException(
                        "com.example.cargotracker.shared.domain.error.IllegalTransition: "
                                + IllegalTransition.MARKER + "追跡 T-1 は既に開始しています",
                        new IllegalStateException(
                                "An exception was thrown by the remote message handling"
                                        + " component: ")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message"))
                .isEqualTo("追跡 T-1 は既に開始しています");
    }

    @Test
    @DisplayName("集約の外で断った状態遷移違反も 409")
    void mapsIllegalTransitionThrownOutsideTheAggregate() {
        ResponseEntity<Map<String, Object>> response =
                handler.onIllegalTransition(new IllegalTransition("開始していません"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("値オブジェクトが断った業務規則違反も 422")
    void mapsBusinessRuleViolationThrownDirectly() {
        ResponseEntity<Map<String, Object>> response =
                handler.onBusinessRuleViolation(new BusinessRuleViolation("追跡番号は必須です"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message")).isEqualTo("追跡番号は必須です");
    }

    @Test
    @DisplayName("文言の無い失敗でも「処理できませんでした」と返す（空を返さない）")
    void fallsBackWhenThereIsNoMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.onCommandFailed(new CommandExecutionException(null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message")).isEqualTo("処理できませんでした");
    }
}
