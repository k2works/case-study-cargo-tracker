package com.example.handlingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Handling Context 固有の値オブジェクト（[ADR-023] 決定 2）。
 *
 * <p>ここで確かめるのは<strong>空を受け付けないこと</strong>と、
 * <strong>復元では検査しないこと</strong>である。復元で検査すると、要件が無かったころの行が
 * 読めなくなる（IT6 の学び）。
 */
@DisplayName("荷役の値オブジェクト")
class HandlingValueObjectTest {

    @Nested
    @DisplayName("予約番号")
    class BookingId {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "　"})
        @DisplayName("空では作れない")
        void rejectsBlank(String value) {
            assertThatThrownBy(() -> CargoBookingId.of(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("復元では検査しない。列が無かったころの行が読めなくなる")
        void restoreDoesNotValidate() {
            assertThat(CargoBookingId.restore(null)).isNull();
            assertThat(CargoBookingId.restore("BKG-2026000001"))
                    .isEqualTo(CargoBookingId.of("BKG-2026000001"));
        }

        @Test
        @DisplayName("画面と記録に出す文字列は、番号そのもの")
        void printsItsValue() {
            assertThat(CargoBookingId.of("BKG-2026000001")).hasToString("BKG-2026000001");
        }
    }

    @Nested
    @DisplayName("追跡番号")
    class TrackingNumber {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("空では作れない。US15-1 は追跡番号を作業の起点にする")
        void rejectsBlank(String value) {
            assertThatThrownBy(() -> HandlingTrackingNumber.of(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 書式まで検査すると、採番の規則が 2 か所に分かれる（[ADR-011] と同じ理由）。 */
        @Test
        @DisplayName("書式は検査しない。採番するのはこちらではない")
        void doesNotValidateFormat() {
            assertThat(HandlingTrackingNumber.of("TRK-20260823-0001"))
                    .hasToString("TRK-20260823-0001");
            assertThat(HandlingTrackingNumber.of("なんでもよい")).hasToString("なんでもよい");
        }
    }

    @Nested
    @DisplayName("航海番号")
    class VoyageNumber {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("空では作れない")
        void rejectsBlank(String value) {
            assertThatThrownBy(() -> HandlingVoyageNumber.of(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("復元では検査しない")
        void restoreDoesNotValidate() {
            assertThat(HandlingVoyageNumber.restore(null)).isNull();
            assertThat(HandlingVoyageNumber.restore("V0100")).hasToString("V0100");
        }
    }

    @Nested
    @DisplayName("荷受人の確認")
    class Confirmation {

        /** 空欄のまま通せると、「通関前の貨物を引き渡した」記録が残る（[ADR-023] 決定 4）。 */
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("空では作れない")
        void rejectsBlank(String value) {
            assertThatThrownBy(() -> ConsigneeConfirmation.of(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("復元では検査しない")
        void restoreDoesNotValidate() {
            assertThat(ConsigneeConfirmation.restore(null)).isNull();
            assertThat(ConsigneeConfirmation.restore("山田太郎")).hasToString("山田太郎");
        }
    }
}
