package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.CarrierName;
import com.example.cargotracker.routing.domain.model.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.Schedule;
import com.example.cargotracker.routing.domain.model.VesselName;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Routing Context のドメインを検証する。
 *
 * <p>局面は中盤（インサイドアウト）。**画面より先に不変条件をここで固める。**
 * `Schedule` の連結制約は画面から作ると必ず崩れる。
 */
@SuppressWarnings("java:S2187")
class VoyageTest {

    private static final Location 大阪 = Location.of("JPOSA");
    private static final Location 上海 = Location.of("CNSHA");
    private static final Location ロサンゼルス = Location.of("USLAX");

    private static Instant 時刻(String iso) {
        return Instant.parse(iso);
    }

    private static CarrierMovement 区間(
            Location from, Location to, String departure, String arrival) {
        return CarrierMovement.of(from, to, 時刻(departure), 時刻(arrival));
    }

    /** 大阪 → 上海 → ロサンゼルス。 */
    private static Schedule 経由あり() {
        return Schedule.of(List.of(
                区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"),
                区間(上海, ロサンゼルス, "2026-09-04T12:00:00Z", "2026-09-16T06:00:00Z")));
    }

    @Nested
    @DisplayName("CarrierMovement（運送区間）")
    class 運送区間 {

        @Test
        void 出発地と到着地と時刻を持つ() {
            CarrierMovement m = 区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z");

            assertThat(m.departureLocation()).isEqualTo(大阪);
            assertThat(m.arrivalLocation()).isEqualTo(上海);
        }

        /** ビジネスルール 3: 出発地と到着地は異なる。 */
        @Test
        void 出発地と到着地が同じ区間を拒否する() {
            assertThatThrownBy(() ->
                    区間(大阪, 大阪, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("出発地と到着地");
        }

        /** 到着が出発より前になる区間は時間が巻き戻る。 */
        @Test
        void 到着が出発より前の区間を拒否する() {
            assertThatThrownBy(() ->
                    区間(大阪, 上海, "2026-09-03T08:00:00Z", "2026-09-01T10:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("到着");
        }

        /** 境界。**出発と到着が同時刻の区間も認めない**（移動していない）。 */
        @Test
        void 出発と到着が同時刻の区間を拒否する() {
            assertThatThrownBy(() ->
                    区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-01T10:00:00Z"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Schedule（航海スケジュール）")
    class 航海スケジュール {

        @Test
        void 最初の区間の出発地が航海の出発地になる() {
            assertThat(経由あり().origin()).isEqualTo(大阪);
        }

        @Test
        void 最後の区間の到着地が航海の目的地になる() {
            assertThat(経由あり().destination()).isEqualTo(ロサンゼルス);
        }

        @Test
        void 途中の港を寄港地として返す() {
            assertThat(経由あり().callingPorts()).containsExactly(上海);
        }

        @Test
        void 直行便には寄港地が無い() {
            Schedule direct = Schedule.of(List.of(
                    区間(大阪, ロサンゼルス, "2026-09-01T10:00:00Z", "2026-09-14T06:00:00Z")));

            assertThat(direct.callingPorts()).isEmpty();
            assertThat(direct.origin()).isEqualTo(大阪);
            assertThat(direct.destination()).isEqualTo(ロサンゼルス);
        }

        /**
         * ビジネスルール 2: 連結制約。
         *
         * <p><strong>区間 1 の到着港と区間 2 の出発港は同じでなければならない。</strong>
         * 違えば貨物は途中で消える。**DB の CHECK では守れない**（区間をまたぐため）。
         */
        @Test
        void 区間がつながっていないスケジュールを拒否する() {
            assertThatThrownBy(() -> Schedule.of(List.of(
                    区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"),
                    // 上海に着いたのに大阪から出発している
                    区間(大阪, ロサンゼルス, "2026-09-04T12:00:00Z", "2026-09-16T06:00:00Z"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("つながって");
        }

        /**
         * <strong>時間も巻き戻ってはならない。</strong>
         *
         * <p>区間 2 の出発が区間 1 の到着より前だと、着く前に次の船が出ている。
         * **これも DB の CHECK では守れない。**
         */
        @Test
        void 前の区間の到着より前に出発するスケジュールを拒否する() {
            assertThatThrownBy(() -> Schedule.of(List.of(
                    区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"),
                    // 上海到着（9/3 08:00）より前に出発している
                    区間(上海, ロサンゼルス, "2026-09-02T12:00:00Z", "2026-09-16T06:00:00Z"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("出発");
        }

        /** 境界。**到着と同時刻の出発は認める**（接続時間 0 は運用上ありうる）。 */
        @Test
        void 前の区間の到着と同時刻の出発は認める() {
            assertThatCode(() -> Schedule.of(List.of(
                    区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"),
                    区間(上海, ロサンゼルス, "2026-09-03T08:00:00Z", "2026-09-16T06:00:00Z"))))
                    .doesNotThrowAnyException();
        }

        @Test
        void 区間が1つも無いスケジュールを拒否する() {
            assertThatThrownBy(() -> Schedule.of(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("区間");
        }

        @Test
        void 同じ港を2度通るスケジュールも受け付ける() {
            // 大阪 → 上海 → 大阪。実務では珍しいが、業務ルールとしては禁じていない
            assertThatCode(() -> Schedule.of(List.of(
                    区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"),
                    区間(上海, 大阪, "2026-09-04T12:00:00Z", "2026-09-06T06:00:00Z"))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("値オブジェクト")
    class 値オブジェクト {

        @ParameterizedTest
        @ValueSource(strings = {"V001", "ABC-123", "0100S"})
        void 航海番号は英数字とハイフンを受け入れる(String value) {
            assertThat(new VoyageNumber(value).value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "V 001", "ｖ００１"})
        void 航海番号の不正な形式を拒否する(String value) {
            assertThatThrownBy(() -> new VoyageNumber(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 航海番号は前後の空白を取り除く() {
            assertThat(new VoyageNumber("  V001  ").value()).isEqualTo("V001");
        }

        @Test
        void 船名と運送会社は必須() {
            assertThatThrownBy(() -> new VesselName(" "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CarrierName(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 貨物種別は3つである() {
            assertThat(RoutingCargoType.values()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Voyage（集約）")
    class 航海 {

        private static RegisterVoyageCommand コマンド(Set<RoutingCargoType> types) {
            return new RegisterVoyageCommand(
                    new VoyageNumber("V001"),
                    new VesselName("さくら丸"),
                    new CarrierName("日本海運"),
                    経由あり(),
                    types);
        }

        @Test
        void 登録すると航海の端点が読み取れる() {
            Voyage voyage = Voyage.register(コマンド(Set.of(RoutingCargoType.GENERAL)));

            assertThat(voyage.origin()).isEqualTo(大阪);
            assertThat(voyage.destination()).isEqualTo(ロサンゼルス);
            assertThat(voyage.callingPorts()).containsExactly(上海);
        }

        @Test
        void 指定した港の出発時刻と到着時刻を返す() {
            Voyage voyage = Voyage.register(コマンド(Set.of(RoutingCargoType.GENERAL)));

            assertThat(voyage.departureTime(大阪)).contains(時刻("2026-09-01T10:00:00Z"));
            assertThat(voyage.arrivalTime(ロサンゼルス)).contains(時刻("2026-09-16T06:00:00Z"));
        }

        @Test
        void 立ち寄らない港の時刻は空を返す() {
            Voyage voyage = Voyage.register(コマンド(Set.of(RoutingCargoType.GENERAL)));

            assertThat(voyage.departureTime(Location.of("DEHAM"))).isEmpty();
        }

        @Test
        void 対応する貨物種別だけを受け入れる() {
            Voyage voyage = Voyage.register(
                    コマンド(Set.of(RoutingCargoType.GENERAL, RoutingCargoType.REFRIGERATED)));

            assertThat(voyage.accepts(RoutingCargoType.GENERAL)).isTrue();
            assertThat(voyage.accepts(RoutingCargoType.REFRIGERATED)).isTrue();
            assertThat(voyage.accepts(RoutingCargoType.HAZARDOUS))
                    .as("危険物を扱えない航海に危険物を載せてはならない")
                    .isFalse();
        }

        /** **何も運べない航海は業務上あり得ない。** */
        @Test
        void 対応貨物種別が空の航海を拒否する() {
            assertThatThrownBy(() -> Voyage.register(コマンド(Set.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("貨物種別");
        }

        @Test
        void 必須項目が欠けた航海を拒否する() {
            assertThatThrownBy(() -> Voyage.register(new RegisterVoyageCommand(
                    null, new VesselName("さくら丸"), new CarrierName("日本海運"),
                    経由あり(), Set.of(RoutingCargoType.GENERAL))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
