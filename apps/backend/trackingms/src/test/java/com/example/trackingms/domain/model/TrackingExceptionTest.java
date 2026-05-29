package com.example.trackingms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TrackingException} エンティティと関連 enum の単体テスト（US19 / US20 / IT6 タスク 2.1）。
 */
class TrackingExceptionTest {

    private static final TrackingExceptionId ID = TrackingExceptionId.generate();
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 25, 9, 0);

    @Test
    @DisplayName("US19: DELAY 例外は escalated = false で REPORTED 状態で生成される")
    void DELAY例外の初期状態() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", "悪天候のため遅延");

        assertThat(ex.getResponseStatus()).isEqualTo(ResponseStatus.REPORTED);
        assertThat(ex.isEscalated()).isFalse();
        assertThat(ex.getResolution()).isNull();
        assertThat(ex.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("US20: LOSS 例外は escalated = true が自動設定される")
    void LOSS例外はescalated自動() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.LOSS, OCCURRED_AT, "SGSIN", "貨物紛失を確認");

        assertThat(ex.isEscalated()).isTrue();
    }

    @Test
    @DisplayName("US20: DAMAGE 例外は escalated = false（管理職通知は不要）")
    void DAMAGE例外はescalatedfalse() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DAMAGE, OCCURRED_AT, "SGSIN", "外装に損傷あり");

        assertThat(ex.isEscalated()).isFalse();
    }

    @Test
    @DisplayName("US19: description が空ならば拒否")
    void descriptionが空は拒否() {
        assertThatThrownBy(() -> new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US19: startResponding() で REPORTED → RESPONDING に遷移する")
    void RESPONDING遷移() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", "悪天候");

        ex.startResponding();

        assertThat(ex.getResponseStatus()).isEqualTo(ResponseStatus.RESPONDING);
    }

    @Test
    @DisplayName("US19: resolve() で RESPONDING → RESOLVED に遷移、resolution と resolvedAt が設定される")
    void RESOLVED遷移() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", "悪天候");
        ex.startResponding();
        LocalDateTime resolveTime = LocalDateTime.of(2026, 7, 26, 14, 0);

        ex.resolve("代替ルート手配済み。新到着予定 2026-08-20", resolveTime);

        assertThat(ex.getResponseStatus()).isEqualTo(ResponseStatus.RESOLVED);
        assertThat(ex.getResolution()).startsWith("代替ルート");
        assertThat(ex.getResolvedAt()).isEqualTo(resolveTime);
    }

    @Test
    @DisplayName("US19: REPORTED から直接 RESOLVED への遷移も許可（軽微な例外）")
    void REPORTEDから直接RESOLVED可能() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", "短時間遅延");

        ex.resolve("自然回復、影響なし", LocalDateTime.now());

        assertThat(ex.getResponseStatus()).isEqualTo(ResponseStatus.RESOLVED);
    }

    @Test
    @DisplayName("ADR-0013: resolution が空で RESOLVED に遷移しようとすると拒否")
    void resolutionが空は拒否() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", "悪天候");

        assertThatThrownBy(() -> ex.resolve("", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US19: RESOLVED 状態から再度 resolve しようとすると拒否")
    void RESOLVEDから再遷移は拒否() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", "悪天候");
        ex.resolve("対応済み", LocalDateTime.now());

        assertThatThrownBy(() -> ex.resolve("追加対応", LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("US19: RESPONDING から RESPONDING への自己遷移は拒否")
    void 同一状態への遷移は拒否() {
        TrackingException ex = new TrackingException(
                ID, ExceptionType.DELAY, OCCURRED_AT, "SGSIN", "悪天候");
        ex.startResponding();

        assertThatThrownBy(ex::startResponding)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ADR-0013: ResponseStatus 単方向遷移ガード（RESOLVED → REPORTED は不可）")
    void 逆行遷移は拒否() {
        assertThat(ResponseStatus.REPORTED.canTransitionTo(ResponseStatus.RESPONDING)).isTrue();
        assertThat(ResponseStatus.REPORTED.canTransitionTo(ResponseStatus.RESOLVED)).isTrue();
        assertThat(ResponseStatus.RESPONDING.canTransitionTo(ResponseStatus.RESOLVED)).isTrue();

        assertThat(ResponseStatus.RESPONDING.canTransitionTo(ResponseStatus.REPORTED)).isFalse();
        assertThat(ResponseStatus.RESOLVED.canTransitionTo(ResponseStatus.REPORTED)).isFalse();
        assertThat(ResponseStatus.RESOLVED.canTransitionTo(ResponseStatus.RESPONDING)).isFalse();
        assertThat(ResponseStatus.REPORTED.canTransitionTo(ResponseStatus.REPORTED)).isFalse();
    }

    @Test
    @DisplayName("US20: ExceptionType.isEscalationRequired() は LOSS のみ true")
    void escalation要否() {
        assertThat(ExceptionType.LOSS.isEscalationRequired()).isTrue();
        assertThat(ExceptionType.DELAY.isEscalationRequired()).isFalse();
        assertThat(ExceptionType.DAMAGE.isEscalationRequired()).isFalse();
    }

    @Test
    @DisplayName("ADR-0013: TrackingExceptionId は UUID 生成可能、null/空は拒否")
    void IDの不変条件() {
        assertThat(TrackingExceptionId.generate().value()).isNotBlank();
        assertThatThrownBy(() -> new TrackingExceptionId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackingExceptionId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US19: replay() でイベント再生時の状態完全復元が可能")
    void イベント再生による状態復元() {
        LocalDateTime resolveTime = LocalDateTime.of(2026, 7, 26, 14, 0);

        TrackingException ex = TrackingException.replay(
                ID, ExceptionType.DAMAGE, OCCURRED_AT, "SGSIN", "破損あり",
                ResponseStatus.RESOLVED, "保険手続き完了", resolveTime);

        assertThat(ex.getResponseStatus()).isEqualTo(ResponseStatus.RESOLVED);
        assertThat(ex.getResolution()).isEqualTo("保険手続き完了");
        assertThat(ex.getResolvedAt()).isEqualTo(resolveTime);
    }
}
