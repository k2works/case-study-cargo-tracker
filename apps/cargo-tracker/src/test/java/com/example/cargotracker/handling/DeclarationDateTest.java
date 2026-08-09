package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.handling.domain.model.CustomsDeclaration;
import com.example.cargotracker.handling.domain.model.DeclarationNumber;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 申告日時に未来を入れられない（IT12 の C36）。
 *
 * <p>荷役の登録は「未来の出来事は履歴に書かせない」と定めているのに、
 * <strong>通関申告の日時と例外対応の新しい到着予定日には同じ守りが無かった</strong>
 * （IT11 レビュー C36 / C37）。
 *
 * <p><strong>向きが逆であることに意味がある。</strong> 申告は<strong>起きた事実</strong>
 * なので未来を拒む。新しい到着予定日は<strong>これからの見込み</strong>なので過去を拒む。
 * 同じ「日付の検査」でも、拒む側が逆である。
 *
 * <p>IT11 のふりかえり T5 の対象である。<strong>時間で守るテストは判別しない</strong>ため、
 * どちらの検査で落ちたのかがメッセージから読める形にする。
 */
@DisplayName("申告日時（C36）")
class DeclarationDateTest {

    private static final Instant NOW = Instant.parse("2026-04-20T09:00:00Z");

    /** <strong>まだ出していない申告を「出した」と記録できてはならない。</strong> */
    @Test
    void 未来の申告日時は記録できない() {
        assertThatThrownBy(() -> CustomsDeclaration.declare(
                new DeclarationNumber("DEC-0001"), NOW.plus(Duration.ofDays(1)), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未来");
    }

    /**
     * <strong>いまこの瞬間の申告は記録できる。</strong>
     *
     * <p>境界を「未来」に倒すと、<strong>出した直後の登録が拒まれる</strong>。
     * 現場は申告を出してすぐ登録する。
     */
    @Test
    void 現在時刻の申告は記録できる() {
        CustomsDeclaration declaration = CustomsDeclaration.declare(
                new DeclarationNumber("DEC-0002"), NOW, NOW);

        assertThat(declaration.declaredAt()).isEqualTo(NOW);
    }

    /** 過去の申告は記録できる。**後から入力するのは日常である。** */
    @Test
    void 過去の申告は記録できる() {
        CustomsDeclaration declaration = CustomsDeclaration.declare(
                new DeclarationNumber("DEC-0003"), NOW.minus(Duration.ofDays(3)), NOW);

        assertThat(declaration.declaredAt()).isBefore(NOW);
    }
}
