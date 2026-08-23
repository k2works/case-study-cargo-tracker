package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 追跡番号（US14・[ADR-021]）。 */
@DisplayName("追跡番号")
class TrackingNumberTest {

    /**
     * 形式そのものが契約になる。
     *
     * <p>荷主に伝える番号であり、荷役・追跡の各サービスが同じ形で照合する。
     * {@code ui_design.md} の例示と揃える。
     */
    @Test
    @DisplayName("TRK-yyyyMMdd-nnnn を受け入れる")
    void acceptsTheAgreedFormat() {
        assertThat(TrackingNumber.of("TRK-20260822-0001").value())
                .isEqualTo("TRK-20260822-0001");
    }

    /** `data-model.md` の `VARCHAR(20)` に収まる。 */
    @Test
    @DisplayName("20 文字に収まる")
    void fitsTheColumn() {
        assertThat(TrackingNumber.of("TRK-20260822-0001").value().length())
                .isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("形式が違えば受け付けない")
    void rejectsOtherFormats() {
        for (String invalid : new String[] {
            null, "", "TRK-2026-0001", "BKG-20260822-0001", "TRK-20260822-1",
            "TRK-20260822-00001", "trk-20260822-0001", " TRK-20260822-0001",
        }) {
            assertThatThrownBy(() -> TrackingNumber.of(invalid))
                    .as("受け付けてはいけない値を通している: %s", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * <strong>復元では検査しない</strong>（[ADR-012]）。
     *
     * <p>形式を後から変えたときに、古い形式の行が読めなくなる。
     */
    @Test
    @DisplayName("復元では検査しない")
    void restoreDoesNotValidate() {
        assertThat(TrackingNumber.restoreNullable("OLD-FORMAT").value()).isEqualTo("OLD-FORMAT");
    }

    @Test
    @DisplayName("未発行は復元しても空のまま")
    void restoresNullAsNull() {
        assertThat(TrackingNumber.restoreNullable(null)).isNull();
    }
}
