package com.example.cargotracker.shared.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 断り方の種類（[ADR-0001] 決定 5 第 12 項）。
 *
 * <p>サービス越しに来た例外は根の型が置き換わるので、種類を文言の接頭辞で運ぶ。
 * その印が<b>画面には出ない</b>ことまで確かめる。</p>
 */
class BusinessErrorTest {

    @Test
    @DisplayName("業務規則違反は印つきで運ばれ、画面には印を出さない")
    void carriesMarkerButDoesNotShowIt() {
        BusinessRuleViolation violation = new BusinessRuleViolation("割引率は 0〜30% です");

        assertThat(violation.getMessage()).startsWith(BusinessRuleViolation.MARKER);
        assertThat(BusinessRuleViolation.strip(violation.getMessage()))
                .isEqualTo("割引率は 0〜30% です");
    }

    @Test
    @DisplayName("状態遷移違反も同じ形")
    void illegalTransitionCarriesItsOwnMarker() {
        IllegalTransition transition = new IllegalTransition("既に受け付けています");

        assertThat(transition.getMessage()).startsWith(IllegalTransition.MARKER);
        assertThat(BusinessRuleViolation.strip(transition.getMessage()))
                .isEqualTo("既に受け付けています");
    }

    @Test
    @DisplayName("印が無い文言はそのまま返す")
    void leavesUnmarkedMessagesAlone() {
        assertThat(BusinessRuleViolation.strip("印のない文言")).isEqualTo("印のない文言");
    }

    @Test
    @DisplayName("文言が無くても落ちない")
    void toleratesNullMessage() {
        // 根の例外が取れないことは起こりうる。ここで NPE を出すと、
        // 断った理由の代わりに 500 が返る。
        assertThat(BusinessRuleViolation.strip(null)).isNull();
    }

    @Test
    @DisplayName("2 つの印は取り違えない")
    void markersAreDistinct() {
        assertThat(BusinessRuleViolation.MARKER).isNotEqualTo(IllegalTransition.MARKER);
        assertThat(new BusinessRuleViolation("x").getMessage())
                .doesNotContain(IllegalTransition.MARKER);
    }
}
