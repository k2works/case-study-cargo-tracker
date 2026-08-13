package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.valueobjects.ClaimCode;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引取確認コード（US35）。
 *
 * <p>IT7 の引取記録は<strong>提示された値をそのまま書き写すだけ</strong>で、
 * 照合する相手がシステムの中に無かった。<strong>記録はできるが証明にならない。</strong>
 *
 * <p><strong>追跡番号とは別の値である。</strong> 追跡番号は荷主が取引先へ転送する
 * ことを前提にした「合鍵」であり（公開追跡）、<strong>それを知っているだけで
 * 引き取れてはならない</strong>。形式も別にする — 追跡番号に似せると、
 * 現場が取り違えて入力する。
 */
@DisplayName("引取確認コード（US35）")
class ClaimCodeTest {

    /** 予測しにくい採番でなければ、総当たりで引き取られる。 */
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();

    /**
     * <strong>追跡番号と見分けがつく形にする。</strong>
     *
     * <p>{@code TRK-} で始まる値を確認コードにすると、現場は追跡番号を
     * そのまま入れてしまう。<strong>入れられる形にしておいて「入れるな」と
     * 教育するのは、仕組みではない。</strong>
     */
    @Test
    void 追跡番号とは違う形をしている() {
        ClaimCode code = ClaimCode.issue(RANDOM);

        assertThat(code.value()).startsWith("CLM-");
        assertThat(code.value()).doesNotStartWith("TRK-");
        assertThat(code.value()).matches("CLM-[0-9A-Z]{8}");
    }

    /**
     * <strong>採番は予測できない。</strong>
     *
     * <p>追跡番号は日付＋連番であり<strong>推測できる形</strong>をしている。
     * 確認コードを同じ作り方にすると、番号を 1 つ知るだけで隣の貨物も引き取れる。
     */
    @Test
    void 採番した値は重複しない() {
        Set<String> issued = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            issued.add(ClaimCode.issue(RANDOM).value());
        }

        assertThat(issued).hasSize(500);
    }

    /**
     * <strong>照合は大小文字と前後の空白を問わない。</strong>
     *
     * <p>コードは電話や紙で伝わる。入力の揺れで拒むと、
     * <strong>正しい荷受人が引き取れず、現場は照合そのものを迂回したくなる</strong>。
     */
    @Test
    void 大小文字と前後の空白は照合に影響しない() {
        ClaimCode code = ClaimCode.of("CLM-1A2B3C4D");

        assertThat(code.matches(" clm-1a2b3c4d ")).isTrue();
        assertThat(code.matches("CLM-1A2B3C4D")).isTrue();
    }

    /**
     * <strong>違う値は一致しない。</strong>
     *
     * <p>常に {@code true} を返す実装だと、上の検査だけでは緑になる。
     * その実装では<strong>照合を入れた意味が丸ごと消える</strong>。
     */
    @Test
    void 違う値は一致しない() {
        ClaimCode code = ClaimCode.of("CLM-1A2B3C4D");

        assertThat(code.matches("CLM-99999999")).isFalse();
        assertThat(code.matches("TRK-20260401-0001")).isFalse();
        assertThat(code.matches("")).isFalse();
        assertThat(code.matches(null)).isFalse();
    }

    /** 形式の違う値は復元できない。**壊れた値を持ったまま照合に進まない。** */
    @Test
    void 形式の違う値は受け付けない() {
        for (String invalid : new String[] {"1A2B3C4D", "CLM-1A2B", "CLM-1a2b3c4d5", "CLM-"}) {
            assertThatThrownBy(() -> ClaimCode.of(invalid))
                    .as("%s は確認コードの形式ではない", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
