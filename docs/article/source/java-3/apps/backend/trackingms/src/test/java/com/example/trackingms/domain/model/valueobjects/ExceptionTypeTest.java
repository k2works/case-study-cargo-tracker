package com.example.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 追跡の例外の種別（US19・US20・[ADR-024] 決定 3・11）。 */
@DisplayName("追跡の例外の種別")
class ExceptionTypeTest {

    /**
     * <strong>対で確かめる</strong>（[ADR-024] 決定 3）。
     *
     * <p>「紛失で立つ」だけを見ると、常に真を返す実装でも緑になる。列挙を全部回して、
     * <strong>紛失以外では立たない</strong>ことを同じ検査で見る。
     */
    @ParameterizedTest
    @EnumSource(ExceptionType.class)
    @DisplayName("緊急なのは紛失だけ")
    void onlyLostIsUrgent(ExceptionType type) {
        assertThat(type.urgent())
                .as("%s の緊急の扱いが違う", type)
                .isEqualTo(type == ExceptionType.LOST);
    }

    /**
     * <strong>手で起票できるのは 3 つだけ</strong>（[ADR-024] 決定 11）。
     *
     * <p>誤配は US28 が、税関保留は US29 が自動で起票する。手で起票できると、
     * 自動の検知と人の起票が混ざる。
     */
    @ParameterizedTest
    @EnumSource(ExceptionType.class)
    @DisplayName("手で起票できるのは遅延・破損・紛失だけ")
    void onlyThreeAreRaisableByOperator(ExceptionType type) {
        boolean expected = type == ExceptionType.DELAY || type == ExceptionType.DAMAGE
                || type == ExceptionType.LOST;

        assertThat(type.raisableByOperator())
                .as("%s の起票の扱いが違う。自動で検知するものを手で起票できるようにしない", type)
                .isEqualTo(expected);
    }

    /** すべての種別が表示名を持つ。決め忘れた種別は、画面に列挙の名前が出る。 */
    @ParameterizedTest
    @EnumSource(ExceptionType.class)
    @DisplayName("すべての種別が表示名を持つ")
    void everyTypeHasALabel(ExceptionType type) {
        assertThat(type.label())
                .as("%s の表示名が決まっていない", type)
                .isNotBlank()
                .isNotEqualTo(type.name());
    }

    @Test
    @DisplayName("種別は 5 つ。増減したら [ADR-024] 決定 11 の表を直す")
    void hasExactlyFiveTypes() {
        assertThat(EnumSet.allOf(ExceptionType.class))
                .containsExactly(ExceptionType.DELAY, ExceptionType.DAMAGE, ExceptionType.LOST,
                        ExceptionType.MISROUTE, ExceptionType.CUSTOMS_HOLD);
    }

    @Test
    @DisplayName("手で起票できる種別だけを返す")
    void listsRaisableTypes() {
        assertThat(ExceptionType.raisableTypes())
                .containsExactly(ExceptionType.DELAY, ExceptionType.DAMAGE, ExceptionType.LOST);
    }

    /**
     * <strong>自動で検知する種別は、入口で断る。</strong>
     *
     * <p>選択肢に出さないだけでは足りない。API を直接叩けば送れるため、
     * 断る側にも規則を置く（[ADR-024] 決定 11）。
     */
    @Test
    @DisplayName("自動で検知する種別は、名前で送っても断る")
    void rejectsAutoDetectedTypes() {
        assertThatThrownBy(() -> ExceptionType.parseRaisable("MISROUTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("手では起票できません");
        assertThatThrownBy(() -> ExceptionType.parseRaisable("CUSTOMS_HOLD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("手では起票できません");
    }

    @Test
    @DisplayName("語彙にある名前は読め、無い名前と空は断る")
    void parsesKnownNamesOnly() {
        assertThat(ExceptionType.parseRaisable("DELAY")).isEqualTo(ExceptionType.DELAY);

        assertThatThrownBy(() -> ExceptionType.parseRaisable("TYPHOON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("例外の種別が不正です");
        assertThatThrownBy(() -> ExceptionType.parseRaisable(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("例外の種別を選んでください");
        assertThatThrownBy(() -> ExceptionType.parseRaisable(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 復元では起票できるかを問わない。自動で起票された行も読める。 */
    @Test
    @DisplayName("復元では、自動で検知する種別も読める")
    void restoresAutoDetectedTypes() {
        assertThat(ExceptionType.restore("MISROUTE")).isEqualTo(ExceptionType.MISROUTE);
        assertThatThrownBy(() -> ExceptionType.restore(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
