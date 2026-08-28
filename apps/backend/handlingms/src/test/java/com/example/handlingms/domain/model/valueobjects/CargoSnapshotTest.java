package com.example.handlingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 貨物の写しと、作業場所の照合（[ADR-023] 決定 2・決定 3）。
 *
 * <p>照合の判定は<strong>ここ 1 か所</strong>に置く。テスト側に同じ判定を書き直すと、
 * 本番が間違っていても検査だけが正しく、素通りする。
 */
@DisplayName("貨物の写し")
class CargoSnapshotTest {

    private static CargoSnapshot tokyoToLosAngelesVia(String transitUnLocode) {
        return CargoSnapshot.of("BKG-2026000001", "JPTYO", "USLAX", List.of(
                new LegSnapshot("V0100", "JPTYO", transitUnLocode),
                new LegSnapshot("V0200", transitUnLocode, "USLAX")));
    }

    @Nested
    @DisplayName("予定どおりの作業")
    class OnRoute {

        @Test
        @DisplayName("出発港での受領は予定どおり")
        void receiveAtOrigin() {
            assertThat(tokyoToLosAngelesVia("CNSHA").isOffRoute(HandlingType.RECEIVE, "JPTYO"))
                    .isFalse();
        }

        @Test
        @DisplayName("旅程の積込港での積込は予定どおり")
        void loadAtItineraryPort() {
            assertThat(tokyoToLosAngelesVia("CNSHA").isOffRoute(HandlingType.LOAD, "CNSHA"))
                    .as("経由港は 2 区間目の積込港である")
                    .isFalse();
        }

        @Test
        @DisplayName("旅程の荷降港での荷降しは予定どおり")
        void unloadAtItineraryPort() {
            assertThat(tokyoToLosAngelesVia("CNSHA").isOffRoute(HandlingType.UNLOAD, "CNSHA"))
                    .isFalse();
        }

        @Test
        @DisplayName("目的港での引取は予定どおり")
        void claimAtDestination() {
            assertThat(tokyoToLosAngelesVia("CNSHA").isOffRoute(HandlingType.CLAIM, "USLAX"))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("予定と違う作業")
    class OffRoute {

        /** 予定外でも拒まない（決定 3）。ここで見るのは「予定外だと分かること」である。 */
        @Test
        @DisplayName("旅程に無い港での荷降しは予定外")
        void unloadAtUnknownPort() {
            assertThat(tokyoToLosAngelesVia("CNSHA").isOffRoute(HandlingType.UNLOAD, "SGSIN"))
                    .isTrue();
        }

        @Test
        @DisplayName("出発港でない場所での受領は予定外")
        void receiveAwayFromOrigin() {
            assertThat(tokyoToLosAngelesVia("CNSHA").isOffRoute(HandlingType.RECEIVE, "CNSHA"))
                    .isTrue();
        }

        @Test
        @DisplayName("目的港でない場所での引取は予定外")
        void claimAwayFromDestination() {
            assertThat(tokyoToLosAngelesVia("CNSHA").isOffRoute(HandlingType.CLAIM, "CNSHA"))
                    .isTrue();
        }

        /**
         * <strong>旅程がまだ無い貨物の積込は予定外とする。</strong>
         *
         * <p>照らす相手が無いことを「予定どおり」と答えると、経路が決まる前に船へ積んでも
         * 記録に何も残らない。分からないときは予定外に倒す。
         */
        @Test
        @DisplayName("旅程が無ければ、積込・荷降しは予定外")
        void withoutItineraryLoadingIsOffRoute() {
            CargoSnapshot notRouted =
                    CargoSnapshot.of("BKG-2026000002", "JPTYO", "USLAX", List.of());

            assertThat(notRouted.isOffRoute(HandlingType.LOAD, "JPTYO")).isTrue();
            assertThat(notRouted.isOffRoute(HandlingType.UNLOAD, "USLAX")).isTrue();
        }

        /** 旅程が無くても、出発港と目的港は予約そのものが持っている。 */
        @Test
        @DisplayName("旅程が無くても、受領と引取は港で照らせる")
        void withoutItineraryReceiveStillChecksOrigin() {
            CargoSnapshot notRouted =
                    CargoSnapshot.of("BKG-2026000002", "JPTYO", "USLAX", List.of());

            assertThat(notRouted.isOffRoute(HandlingType.RECEIVE, "JPTYO")).isFalse();
            assertThat(notRouted.isOffRoute(HandlingType.CLAIM, "USLAX")).isFalse();
        }
    }

    @Test
    @DisplayName("予約番号・出発地・目的地は必須")
    void requiresIdentity() {
        assertThatThrownBy(() -> CargoSnapshot.of(null, "JPTYO", "USLAX", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CargoSnapshot.of("BKG-2026000001", "", "USLAX", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CargoSnapshot.of("BKG-2026000001", "JPTYO", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * <strong>検査を素通りして作れる経路を残さない。</strong>
     *
     * <p>レコードの正準コンストラクタは公開されているため、{@code of} を通さずに
     * 生成できてしまう。「みんな {@code of} を使う」で守られているものは、
     * <strong>使わなかった一箇所</strong>で破れる。
     */
    @Test
    @DisplayName("正準コンストラクタでも検査を素通りできない")
    void cannotBypassValidationThroughTheCanonicalConstructor() {
        assertThatThrownBy(() -> new CargoSnapshot(null, "JPTYO", "USLAX", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CargoSnapshot("BKG-2026000001", " ", "USLAX", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CargoSnapshot("BKG-2026000001", "JPTYO", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
