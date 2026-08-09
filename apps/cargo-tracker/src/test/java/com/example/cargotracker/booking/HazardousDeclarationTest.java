package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.HazardousDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 危険物申告の妥当性（US05 / IT12 の C6）。
 *
 * <p>IT9 のレビューが<strong>危険物クラスの妥当性が未検証</strong>だと指摘し、
 * 3 イテレーション繰り越した。
 *
 * <p><strong>危険物クラスと UN 番号は輸送書類にそのまま載る。</strong>
 * 存在しないクラスや桁の欠けた番号を書いた書類は、
 * <strong>申告が無いのと同じ結果</strong>（積み込み拒否・税関で止まる）になる。
 */
@DisplayName("危険物申告の妥当性（C6）")
class HazardousDeclarationTest {


    /** 国連分類は 1〜9 であり、区分（{@code 5.1} など）を持つものがある。 */
    @Test
    void 国連分類のクラスは通る() {
        for (String hazardClass : new String[] {"1", "2.3", "3", "4.1", "5.1", "6.1", "7", "8", "9"}) {
            assertThat(new HazardousDeclaration(hazardClass, "UN1263", "PAINT").hazardClass())
                    .isEqualTo(hazardClass);
        }
    }

    /**
     * <strong>存在しないクラスは通らない。</strong>
     *
     * <p>危険物クラスは輸送書類にそのまま載る。存在しないクラスを書いた書類は、
     * <strong>申告が無いのと同じ結果</strong>（積み込み拒否・法令違反）になる。
     */
    @Test
    void 国連分類にないクラスは通らない() {
        // **前後の空白は拒まない。** 入力の揺れであって、別のクラスを指すわけではない
        assertThat(new HazardousDeclaration(" 3 ", "UN1263", "PAINT").hazardClass())
                .isEqualTo("3");
        for (String invalid : new String[] {"0", "10", "3.9", "1.7", "引火性液体", "III"}) {
            assertThatThrownBy(() -> new HazardousDeclaration(invalid, "UN1263", "PAINT"))
                    .as("クラス %s は国連分類に無い", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * <strong>UN 番号は {@code UN} ＋ 4 桁である。</strong>
     *
     * <p>桁が欠けた番号は別の物質を指すか、どの物質も指さない。
     * <strong>書類の受理は税関で行われ、そこで止まると貨物は港に残る。</strong>
     */
    @Test
    void 番号の形式が違うものは通らない() {
        for (String invalid : new String[] {"1263", "UN126", "UN12634", "UNABCD"}) {
            assertThatThrownBy(() -> new HazardousDeclaration("3", invalid, "PAINT"))
                    .as("UN 番号 %s は形式が違う", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        // **小文字を拒まない。** 入力の揺れであって、別の物質を指すわけではない
        assertThat(new HazardousDeclaration("3", "un1263", "PAINT").unNumber())
                .isEqualTo("UN1263");
    }

    /**
     * <strong>形式の誤りは「申告が無い」に倒さない。</strong>
     *
     * <p>{@code ofNullable} が空を返すと、呼び出し側は「入力されていない」として
     * 扱う。<strong>誤った申告を黙って捨てると、危険物が一般貨物として運ばれる。</strong>
     */
    @Test
    void 形式の誤りは入力なしとして扱わない() {
        assertThatThrownBy(() -> HazardousDeclaration.ofNullable("99", "UN1263", "PAINT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
