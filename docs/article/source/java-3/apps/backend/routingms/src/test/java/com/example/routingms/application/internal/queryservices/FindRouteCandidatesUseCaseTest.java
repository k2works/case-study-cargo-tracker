package com.example.routingms.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.routingms.domain.repository.LocationRepository;
import com.example.routingms.domain.repository.VoyageRepository;
import com.example.routingms.domain.model.valueobjects.VoyageSearchCriteria;
import com.example.routingms.domain.model.valueobjects.CargoType;
import com.example.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 経路候補算出のユースケース（US08）。
 *
 * <p>ここで確かめるのは「画面の言葉をドメインの言葉に直せているか」である。制約の判断は
 * ドメインの単体テストが持つ。
 */
@DisplayName("経路候補算出（ユースケース）")
class FindRouteCandidatesUseCaseTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private final List<Voyage> stored = new ArrayList<>();

    /**
     * 偽の保存先。**本物と同じ絞りを掛ける。**
     *
     * <p>すべて返す偽物は本物より甘い。本物が落とす航海を通すため、期限の変換が
     * 間違っていても検査は緑になる（IT3 で同じ形の見落としがあった）。
     */
    private final VoyageRepository voyages = new VoyageRepository() {
        @Override
        public Voyage save(Voyage voyage) {
            stored.add(voyage);
            return voyage;
        }

        @Override
        public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
            return stored.stream().filter(v -> v.voyageNumber().equals(voyageNumber)).findFirst();
        }

        @Override
        public List<Voyage> search(VoyageSearchCriteria criteria, int limit) {
            return List.copyOf(stored);
        }

        @Override
        public int countMatching(VoyageSearchCriteria criteria) {
            return stored.size();
        }

        @Override
        public List<Voyage> findCandidates(RouteSearchSpecification specification,
                Instant notDepartedBefore) {
            return stored.stream()
                    .filter(v -> v.supports(specification.cargoType()))
                    .filter(v -> v.departureTimeAt(0)
                            .map(departure -> !departure.isAfter(specification.arrivalDeadline())
                                    && !departure.isBefore(notDepartedBefore))
                            .orElse(false))
                    .toList();
        }
    };

    private final LocationRepository locations = new LocationRepository() {
        @Override
        public List<Location> findAll() {
            return List.of(TOKYO, LOS_ANGELES);
        }

        @Override
        public Optional<Location> findByUnLocode(String unLocode) {
            return findAll().stream().filter(l -> l.unLocode().equals(unLocode)).findFirst();
        }

        @Override
        public Optional<java.time.ZoneId> timeZoneOf(String unLocode) {
            // ロサンゼルスは東京より西。期限の「当日」がずれることを確かめられる
            return "USLAX".equals(unLocode)
                    ? Optional.of(java.time.ZoneId.of("America/Los_Angeles"))
                    : Optional.of(BUSINESS_ZONE);
        }
    };

    /** 「今日」を固定する。テストが実時刻に依存すると、日付が変わった瞬間に落ちる。 */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), BUSINESS_ZONE);

    private final FindRouteCandidatesUseCase useCase =
            new FindRouteCandidatesUseCase(voyages, locations, BUSINESS_ZONE, CLOCK);

    private void give(String number, String departure, String arrival) {
        stored.add(Voyage.register(VoyageNumber.of(number), "船 " + number, "運送会社",
                Set.of(CargoType.GENERAL),
                Schedule.of(List.of(CarrierMovement.of(TOKYO, LOS_ANGELES,
                        Instant.parse(departure), Instant.parse(arrival))))));
    }

    private FindRouteCandidatesUseCase.Result findBy(String deadline) {
        return useCase.find("JPTYO", "USLAX", LocalDate.parse(deadline), CargoType.GENERAL, null, null);
    }

    @Test
    @DisplayName("条件に合う経路を推奨順で返す")
    void returnsRankedCandidates() {
        give("V-LATE", "2026-09-15T09:00:00Z", "2026-09-20T09:00:00Z");
        give("V-EARLY", "2026-09-15T09:00:00Z", "2026-09-16T09:00:00Z");

        FindRouteCandidatesUseCase.Result result = findBy("2026-09-30");

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().get(0).voyageNumbers())
                .containsExactly(VoyageNumber.of("V-EARLY"));
    }

    /**
     * 期限は日付で受け取り、業務タイムゾーンの当日終わりまでとする（[ADR-017] 決定 3）。
     *
     * <p>UTC で判断すると、日本時間の 9 月 30 日 20:00 に着く便（UTC では 11:00）は
     * 通るが、9 月 30 日 23:00 に着く便（UTC では 14:00）も通る一方、**タイムゾーンを
     * 取り違えると当日の遅い便だけが黙って消える**。境界そのもので確かめる。
     */
    @Test
    @DisplayName("期限当日の遅い時刻に着く便も候補に出る（業務タイムゾーンの当日終わりまで）")
    void includesArrivalLateOnTheDeadlineDate() {
        // 目的地（ロサンゼルス）の 2026-09-30 23:59 着 = 2026-10-01 06:59Z。
        // 期限は目的地の暦で判断する（ADR-010）
        give("V-LAST-MINUTE", "2026-09-15T09:00:00Z", "2026-10-01T06:59:00Z");

        assertThat(findBy("2026-09-30").candidates()).hasSize(1);
    }

    @Test
    @DisplayName("期限の翌日に着く便は候補に出ない")
    void excludesArrivalOnTheNextDay() {
        // 目的地（ロサンゼルス）の 2026-10-01 00:30 着 = 2026-10-01 07:30Z
        give("V-JUST-OVER", "2026-09-15T09:00:00Z", "2026-10-01T07:30:00Z");

        assertThat(findBy("2026-09-30").candidates()).isEmpty();
    }

    /**
     * 「無い」は正常な結果である。
     *
     * <p>例外にすると、画面は「エラーが起きた」と伝えることになる。経路設計者に必要なのは
     * 「この条件では見つからなかった」であり、そこから条件を緩める操作へ進めることである。
     */
    @Test
    @DisplayName("候補が無くても、使った条件とともに正常に返す")
    void returnsEmptyResultWithTheCriteria() {
        FindRouteCandidatesUseCase.Result result = findBy("2026-09-30");

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.specification().origin()).isEqualTo(TOKYO);
        assertThat(result.specification().destination()).isEqualTo(LOS_ANGELES);
        assertThat(result.specification().maxTransshipments())
                .isEqualTo(RouteSearchSpecification.DEFAULT_MAX_TRANSSHIPMENTS);
    }

    @Test
    @DisplayName("積み替えの上限を指定できる（条件を緩めた再算出のため）")
    void acceptsALooserTransshipmentLimit() {
        FindRouteCandidatesUseCase.Result result =
                useCase.find("JPTYO", "USLAX", LocalDate.parse("2026-09-30"), CargoType.GENERAL, 3, null);

        assertThat(result.specification().maxTransshipments()).isEqualTo(3);
    }

    /**
     * 存在しない港は、打ち間違いとして伝える。
     *
     * <p>そのまま探索すると結果は必ず 0 件になり、経路設計者には「経路が無い」としか
     * 見えない。打ち間違いだと気づけない。
     */
    @Test
    @DisplayName("マスタに無い港を指定したら、経路が無いのではなく港が無いと伝える")
    void rejectsUnknownPort() {
        LocalDate deadline = LocalDate.parse("2026-09-30");

        assertThatThrownBy(() -> useCase.find("XXXXX", "USLAX", deadline, CargoType.GENERAL, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発地が見つかりません");
    }

    @Test
    @DisplayName("期限と貨物種別は必須")
    void requiresDeadlineAndCargoType() {
        LocalDate deadline = LocalDate.parse("2026-09-30");

        assertThatThrownBy(() -> useCase.find("JPTYO", "USLAX", null, CargoType.GENERAL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.find("JPTYO", "USLAX", deadline, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * すでに出てしまった船を候補に出さない。
     *
     * <p>航海スケジュールの一覧は既定で「本日以降に出発する便」に絞っている。経路候補だけが
     * 過去の便を混ぜると、<strong>押さえられない船を前提にした経路が 1 位に出る</strong>。
     * 古い便ほど日数計算上は早く着くため、上位を占める。一度これに当たった経路設計者は、
     * 以後この一覧の順位を信用しなくなる。
     */
    @Test
    @DisplayName("すでに出発した便を使う経路は候補に出ない")
    void excludesVoyagesThatHaveAlreadyDeparted() {
        // 固定した「今日」は 2026-09-10。9 月 5 日に出た便はもう押さえられない
        give("V-DEPARTED", "2026-09-05T09:00:00Z", "2026-09-18T09:00:00Z");
        give("V-UPCOMING", "2026-09-15T09:00:00Z", "2026-09-28T09:00:00Z");

        assertThat(findBy("2026-09-30").candidates())
                .singleElement()
                .satisfies(path -> assertThat(path.voyageNumbers())
                        .containsExactly(VoyageNumber.of("V-UPCOMING")));
    }

    @Test
    @DisplayName("出発希望日より前に出る便は候補にしない（残作業 5）")
    void excludesVoyagesDepartingBeforeTheRequestedDate() {
        // 荷主が「9 月 20 日以降でないと倉庫に入らない」と言っているのに 9 月 15 日発の便を
        // 候補に出すと、押さえても積むものがない
        give("V-TOO-EARLY", "2026-09-15T09:00:00Z", "2026-09-25T09:00:00Z");
        give("V-OK", "2026-09-21T09:00:00Z", "2026-09-28T09:00:00Z");

        FindRouteCandidatesUseCase.Result result = useCase.find("JPTYO", "USLAX",
                LocalDate.parse("2026-09-30"), CargoType.GENERAL, null,
                LocalDate.parse("2026-09-20"));

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).voyageNumbers().get(0).value()).isEqualTo("V-OK");
    }

    @Test
    @DisplayName("出発希望日の当日に出る便は候補にする")
    void includesVoyageDepartingOnTheRequestedDate() {
        // 業務タイムゾーンでその日の始まりが境目。9/20 01:00 JST は業務上「当日」だが、
        // UTC で判断すると 9/19 16:00 になり、当日の早い便が黙って消える
        give("V-SAME-DAY", "2026-09-19T16:00:00Z", "2026-09-28T09:00:00Z");

        FindRouteCandidatesUseCase.Result result = useCase.find("JPTYO", "USLAX",
                LocalDate.parse("2026-09-30"), CargoType.GENERAL, null,
                LocalDate.parse("2026-09-20"));

        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    @DisplayName("出発希望日の指定が無ければ、出発の早さでは絞らない")
    void withoutRequestedDateDoesNotFilterByDeparture() {
        give("V-EARLY", "2026-09-15T09:00:00Z", "2026-09-25T09:00:00Z");

        assertThat(findBy("2026-09-30").candidates()).hasSize(1);
    }

    /**
     * 期限は**目的地の暦**で判断する（[ADR-010]）。
     *
     * <p>単一の業務タイムゾーン（Asia/Tokyo）で判断すると、目的地が西にずれた分だけ
     * 「当日」が早く終わり、bookingms が受け入れる便をここが候補から落とす。
     * その経路は画面に出ないため、経路設計者には「その経路は無い」としか見えない。
     */
    @Test
    @DisplayName("期限の当日は目的地の暦で終わる（業務タイムゾーンで切らない）")
    void usesDestinationCalendarForTheDeadline() {
        // 2030-09-20 23:00（ロサンゼルス）= 2030-09-21 15:00Z。
        // 東京の暦で切ると 2030-09-20 14:59:59Z までなので、この便は落ちる
        give("V-LATE-AT-DESTINATION", "2030-09-15T09:00:00Z", "2030-09-21T06:00:00Z");

        assertThat(findBy("2030-09-20").candidates())
                .as("目的地の暦では期限内なのに落としている")
                .hasSize(1);
    }
}
