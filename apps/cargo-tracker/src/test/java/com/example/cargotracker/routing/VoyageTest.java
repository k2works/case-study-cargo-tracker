package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierName;
import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
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
                    types,
                    RoutingWeight.ofKilograms(new java.math.BigDecimal("100000")));
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
                    経由あり(), Set.of(RoutingCargoType.GENERAL),
                    RoutingWeight.ofKilograms(new java.math.BigDecimal("100000")))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** 運航変更（US25）。**画面で先に弾かれる条件は集約の守りを判別しない。** */
    @Nested
    @DisplayName("reschedule（運航変更）")
    class 運航変更 {

        private Voyage 便() {
            return Voyage.register(new RegisterVoyageCommand(
                    new VoyageNumber("V001"),
                    new VesselName("さくら丸"),
                    new CarrierName("日本海運"),
                    経由あり(),
                    Set.of(RoutingCargoType.GENERAL),
                    RoutingWeight.ofKilograms(new java.math.BigDecimal("100000"))));
        }

        /**
         * 出港前の時刻。
         *
         * <p>{@code reschedule} は現在時刻を必ず要求する（省略できる 1 引数版は
         * IT10 で削除した。**省略できる安全装置はいずれ省略される**）。
         * 出港済みの守りを主題としないテストでは、ここを渡して条件をそろえる。
         */
        private Instant 出港前() {
            return 時刻("2026-08-01T00:00:00Z");
        }

        private RegisterVoyageCommand 変更(Voyage voyage, VoyageNumber number, String vessel) {
            return new RegisterVoyageCommand(
                    number,
                    new VesselName(vessel),
                    voyage.carrierName(),
                    voyage.schedule(),
                    voyage.acceptableCargoTypes(),
                    voyage.capacityWeight());
        }

        /**
         * <strong>航海番号は変更できない。</strong>
         *
         * <p>変えられると、更新のつもりで**別の便を上書きできる**。画面も番号の
         * 一致を確かめるが、画面で先に弾かれる条件では集約の守りを判別できない。
         */
        @Test
        void 航海番号を変える更新は受け付けない() {
            Voyage voyage = 便();

            // 返り値をそのまま捨てない（捨てると「呼んだだけ」に見える）
            assertThatThrownBy(() -> assertThat(
                    voyage.reschedule(変更(voyage, new VoyageNumber("V999"), "あさひ丸"), 出港前()))
                    .isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("航海番号は変更できません");
        }

        /**
         * <strong>出港済みの区間は変えられない。</strong>
         *
         * <p>すでに出た船の出発時刻を後から書き換えると、**起きた事実と記録が食い違う**。
         * 荷役の記録はその時刻を前提に並んでおり、変えると時系列が崩れる。
         */
        @Test
        void 出港済みの区間は変更できない() {
            Voyage voyage = 便();
            // 最初の区間（2026-09-01 発）はすでに出港している
            Instant now = 時刻("2026-09-02T00:00:00Z");

            var command = new RegisterVoyageCommand(
                    voyage.voyageNumber(),
                    voyage.vesselName(),
                    voyage.carrierName(),
                    Schedule.of(List.of(
                            区間(大阪, 上海, "2026-09-01T12:00:00Z", "2026-09-03T08:00:00Z"),
                            区間(上海, ロサンゼルス,
                                    "2026-09-04T12:00:00Z", "2026-09-16T06:00:00Z"))),
                    voyage.acceptableCargoTypes(),
                    voyage.capacityWeight());

            assertThatThrownBy(() -> assertThat(voyage.reschedule(command, now)).isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("出港済みの区間");
        }

        /**
         * <strong>現在時刻を省いた更新は受け付けない</strong>（IT9 レビュー M3 の返済）。
         *
         * <p>かつては {@code now} を省ける 1 引数版があり、そこを通ると
         * 上のテストが守っている「出港済みの区間は変えられない」が
         * <strong>丸ごと無効になった</strong>。安全装置に「省略」の入口があると、
         * 呼ぶ側は必ず楽なほうを選ぶ。
         */
        @Test
        void 現在時刻を渡さない更新は受け付けない() {
            Voyage voyage = 便();
            var command = 変更(voyage, voyage.voyageNumber(), "あさひ丸");

            assertThatThrownBy(() -> assertThat(voyage.reschedule(command, null)).isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("現在時刻は必須です");
        }

        /** これから出発する区間は変えられる。**変更そのものを止めない。** */
        @Test
        void まだ出発していない区間は変更できる() {
            Voyage voyage = 便();
            Instant now = 時刻("2026-09-02T00:00:00Z");

            Voyage updated = voyage.reschedule(new RegisterVoyageCommand(
                    voyage.voyageNumber(),
                    voyage.vesselName(),
                    voyage.carrierName(),
                    Schedule.of(List.of(
                            区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"),
                            区間(上海, ロサンゼルス,
                                    "2026-09-05T12:00:00Z", "2026-09-18T06:00:00Z"))),
                    voyage.acceptableCargoTypes(),
                    voyage.capacityWeight()), now);

            assertThat(updated.schedule().carrierMovements().getLast().arrivalTime())
                    .isEqualTo(時刻("2026-09-18T06:00:00Z"));
        }

        /** 番号が同じなら内容を入れ替えられる。**元の航海は変えない**（値として扱う）。 */
        @Test
        void 同じ航海番号なら内容を入れ替えられる() {
            Voyage voyage = 便();

            Voyage updated = voyage.reschedule(変更(voyage, voyage.voyageNumber(), "あさひ丸"), 出港前());

            assertThat(updated.vesselName().value()).isEqualTo("あさひ丸");
            assertThat(voyage.vesselName().value()).isEqualTo("さくら丸");
        }

        /** 変わった項目だけが差分に出る。 */
        @Test
        void 差分は変わった項目だけを持つ() {
            Voyage voyage = 便();

            var change = voyage.changesTo(
                    voyage.reschedule(変更(voyage, voyage.voyageNumber(), "あさひ丸"), 出港前()));

            assertThat(change.items()).singleElement()
                    .satisfies(item -> {
                        assertThat(item.label()).isEqualTo("船名");
                        assertThat(item.before()).isEqualTo("さくら丸");
                        assertThat(item.after()).isEqualTo("あさひ丸");
                    });
        }

        /** 何も変えなければ差分は空である。**同じ内容での上書きは業務上意味がない。** */
        @Test
        void 変更が無ければ差分は空になる() {
            Voyage voyage = 便();

            assertThat(voyage.changesTo(
                    voyage.reschedule(変更(voyage, voyage.voyageNumber(), "さくら丸"), 出港前()))
                    .isEmpty()).isTrue();
        }
    }
}
