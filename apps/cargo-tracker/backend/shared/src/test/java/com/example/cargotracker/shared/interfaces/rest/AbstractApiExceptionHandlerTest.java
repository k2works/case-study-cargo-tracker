package com.example.cargotracker.shared.interfaces.rest;

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
 * API のエラー対応表（architecture_backend.md「例外と HTTP の対応」）。
 *
 * <p><b>4 サービスから共有カーネルへ移したので、検査もここへ移す</b>（IT10 T9）。
 * 移す前は各サービスの統合テストが踏んでいて、`shared` 単体では 1 分岐も
 * 通っていなかった——カバレッジの閾値がそれを教えた。</p>
 *
 * <p>ここで確かめるのは<b>包みの解き方</b>である。集約の中で投げた例外は
 * {@code CommandExecutionException} に包まれ、サービス越しでは根の型まで
 * 置き換わる。型で分けると 409 が 422 に劣化する（IT3 で実測）。</p>
 */
class AbstractApiExceptionHandlerTest {

    /** 具体クラスは各サービスが持つ。ここでは中身だけを試す。 */
    private final AbstractApiExceptionHandler handler = new AbstractApiExceptionHandler() { };

    private static String messageOf(ResponseEntity<Map<String, Object>> response) {
        return String.valueOf(response.getBody().get("message"));
    }

    private static String codeOf(ResponseEntity<Map<String, Object>> response) {
        return String.valueOf(response.getBody().get("code"));
    }

    @Test
    @DisplayName("業務規則違反は 422（印は利用者に見せない）")
    void mapsBusinessRuleViolationTo422() {
        var response = handler.onBusinessRuleViolation(
                new BusinessRuleViolation("作業日時に未来は指定できません"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(codeOf(response)).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(messageOf(response)).isEqualTo("作業日時に未来は指定できません");
    }

    @Test
    @DisplayName("状態遷移違反は 409（やり直せば通るのか、入力が悪いのかを分ける）")
    void mapsIllegalTransitionTo409() {
        var response = handler.onIllegalTransition(new IllegalTransition("未受領から引取済へは動けません"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo("ILLEGAL_STATE");
        assertThat(messageOf(response)).isEqualTo("未受領から引取済へは動けません");
    }

    @Test
    @DisplayName("集約が断った業務規則違反は 422（包みを解いて理由を出す）")
    void unwrapsBusinessRuleViolationFromCommandFailure() {
        var response = handler.onCommandFailed(new CommandExecutionException(
                "コマンドが失敗しました",
                new BusinessRuleViolation("引取には荷受人の確認が必要です")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(messageOf(response)).isEqualTo("引取には荷受人の確認が必要です");
    }

    @Test
    @DisplayName("包みが 2 枚以上でも 409 が 422 に化けない（IT3 で実測した形）")
    void findsTheMarkerThroughMultipleWrappers() {
        // **連鎖のいちばん外側だけを見ない。** 直下の cause だけを読むと印に届かない。
        var wrapped = new IllegalStateException("remote",
                new IllegalTransition("未受領から引取済へは動けません"));
        var response = handler.onCommandFailed(
                new CommandExecutionException("コマンドが失敗しました", wrapped));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo("ILLEGAL_STATE");
        assertThat(messageOf(response)).isEqualTo("未受領から引取済へは動けません");
    }

    @Test
    @DisplayName("器だけの文言が最深にあっても、断った理由が届く（IT7 のクラスタで実測）")
    void prefersTheMarkedMessageOverTheDeepestOne() {
        // 遠隔から来ると「An exception was thrown by the remote ...」が最深に来る。
        var remote = new IllegalStateException(
                "An exception was thrown by the remote message handling component: ");
        var marked = new BusinessRuleViolation("引取には荷受人の確認が必要です");
        var chain = new IllegalStateException(marked.getMessage(), remote);

        var response = handler.onCommandFailed(
                new CommandExecutionException("コマンドが失敗しました", chain));

        assertThat(messageOf(response))
                .as("器だけの文言を出すと、断った理由が利用者に届かない")
                .isEqualTo("引取には荷受人の確認が必要です");
    }

    @Test
    @DisplayName("印より前は切り落とす（クラス名を業務担当者に見せない）")
    void stripsEverythingBeforeTheMarker() {
        var withClassName = new IllegalStateException(
                "com.example.cargotracker.shared.domain.error.IllegalTransition: "
                        + IllegalTransition.MARKER + "未受領から引取済へは動けません");

        var response = handler.onCommandFailed(
                new CommandExecutionException("コマンドが失敗しました", withClassName));

        assertThat(messageOf(response))
                .doesNotContain("com.example.cargotracker")
                .isEqualTo("未受領から引取済へは動けません");
    }

    @Test
    @DisplayName("理由が 1 つも無ければ、壊れたとは言わずに一般の文言を返す")
    void fallsBackWhenThereIsNoMessage() {
        var response = handler.onCommandFailed(new CommandExecutionException(null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(messageOf(response)).isEqualTo("処理できませんでした");
    }

    // **自己参照の連鎖は検査しない。** Java は initCause での自己参照を禁じており、
    // getCause を上書きして作ると、例外を組み立てる側が先に溢れる（実測）。
    // 実装の安全装置（t.getCause() == t で打ち切る）は残すが、到達できない形を
    // 無理に作った検査は、何も判別しないまま壊れやすい検査になる。
}
