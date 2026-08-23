package com.example.handlingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 荷役作業の記録（US15・US16・[ADR-023]）。
 *
 * <p>ここで確かめるのは<strong>何を断り、何を記録に残すか</strong>である。荷役の記録は
 * 実際に起きた作業の記録であり、あとから「無かったこと」にはできない。
 */
@DisplayName("荷役作業")
class HandlingActivityTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Instant NOW = Instant.parse("2026-08-23T02:00:00Z");

    private static CargoSnapshot cargo() {
        return CargoSnapshot.of("BKG-2026000001", "JPTYO", "USLAX", List.of(
                new LegSnapshot("V0100", "JPTYO", "CNSHA"),
                new LegSnapshot("V0200", "CNSHA", "USLAX")));
    }

    @Nested
    @DisplayName("種別ごとの要件（ADR-023 決定 1）")
    class TypeRequirements {

        @Test
        @DisplayName("受領は航海番号なしで記録できる")
        void receiveNeedsNoVoyageNumber() {
            HandlingActivity activity = HandlingActivity.register(cargo(), HandlingType.RECEIVE,
                    TOKYO, NOW, "handler01", null, null);

            assertThat(activity.type()).isEqualTo(HandlingType.RECEIVE);
            assertThat(activity.voyageNumber()).isEmpty();
            assertThat(activity.offRoute()).isFalse();
        }

        /** どの船に載せたか分からないと、貨物を追えない。 */
        @Test
        @DisplayName("航海番号のない積込は断る")
        void loadRequiresVoyageNumber() {
            CargoSnapshot cargo = cargo();

            assertThatThrownBy(() -> HandlingActivity.register(cargo, HandlingType.LOAD,
                    TOKYO, NOW, "handler01", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("航海番号");
        }

        @Test
        @DisplayName("航海番号のある積込は記録できる")
        void loadWithVoyageNumber() {
            HandlingActivity activity = HandlingActivity.register(cargo(), HandlingType.LOAD,
                    TOKYO, NOW, "handler01", HandlingVoyageNumber.of("V0100"), null);

            assertThat(activity.voyageNumber()).contains(HandlingVoyageNumber.of("V0100"));
        }

        @Test
        @DisplayName("作業者名は必須。誰が記録したか分からない記録は監査に使えない")
        void requiresOperatorName() {
            CargoSnapshot cargo = cargo();

            assertThatThrownBy(() -> HandlingActivity.register(cargo, HandlingType.RECEIVE,
                    TOKYO, NOW, " ", null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("引取の確認（ADR-023 決定 4・成功基準 3）")
    class ClaimConfirmation {

        /**
         * <strong>断りを外すと赤になる。</strong>
         *
         * <p>通関ガード（US29・IT9）が無い IT7 では、これが唯一の歯止めである。
         * 空欄のまま通せると「通関前の貨物を引き渡した」記録が残る。
         */
        @Test
        @DisplayName("荷受人の確認がない引取は断る")
        void claimRequiresConsigneeConfirmation() {
            CargoSnapshot cargo = cargo();

            assertThatThrownBy(() -> HandlingActivity.register(cargo, HandlingType.CLAIM,
                    LOS_ANGELES, NOW, "handler01", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("荷受人");
        }

        @Test
        @DisplayName("荷受人の確認があれば引取を記録できる")
        void claimWithConfirmation() {
            HandlingActivity activity = HandlingActivity.register(cargo(), HandlingType.CLAIM,
                    LOS_ANGELES, NOW, "handler01", null,
                    ConsigneeConfirmation.of("山田太郎（受取担当）"));

            assertThat(activity.consigneeConfirmation())
                    .contains(ConsigneeConfirmation.of("山田太郎（受取担当）"));
        }

        /** 引取以外に確認は要らない。要求すると、受領のたびに荷受人へ連絡することになる。 */
        @Test
        @DisplayName("受領には荷受人の確認は要らない")
        void receiveNeedsNoConfirmation() {
            assertThatCode(() -> HandlingActivity.register(cargo(), HandlingType.RECEIVE,
                    TOKYO, NOW, "handler01", null, null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("予定と違う場所（ADR-023 決定 3）")
    class OffRoute {

        /**
         * <strong>拒まずに記録し、予定外だったことを残す。</strong>
         *
         * <p>現場ではすでに作業が終わっている。拒むと実際に起きたことがどこにも残らず、
         * 作業員は嘘の場所を入れて通すことになる。
         */
        @Test
        @DisplayName("旅程に無い港での荷降しも記録し、予定外だったことを残す")
        void recordsOffRouteWork() {
            HandlingActivity activity = HandlingActivity.register(cargo(), HandlingType.UNLOAD,
                    Location.of("SGSIN", "Singapore"), NOW, "handler01",
                    HandlingVoyageNumber.of("V0100"), null);

            assertThat(activity.offRoute())
                    .as("予定外だったことが記録に残っていない。US28 で判定し直すことになる")
                    .isTrue();
        }

        @Test
        @DisplayName("予定どおりの作業は予定外にしない")
        void doesNotFlagOnRouteWork() {
            HandlingActivity activity = HandlingActivity.register(cargo(), HandlingType.UNLOAD,
                    SHANGHAI, NOW, "handler01", HandlingVoyageNumber.of("V0100"), null);

            assertThat(activity.offRoute()).isFalse();
        }
    }

    @Test
    @DisplayName("記録は貨物の予約番号を持つ。追跡番号だけでは他サービスと突き合わせられない")
    void keepsBookingId() {
        HandlingActivity activity = HandlingActivity.register(cargo(), HandlingType.RECEIVE,
                TOKYO, NOW, "handler01", null, null);

        assertThat(activity.bookingId()).isEqualTo(CargoBookingId.of("BKG-2026000001"));
    }
}
