package com.example.cargotracker.routing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.events.VoyageCancelledEvent;
import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageSearchCriteria;
import com.example.cargotracker.routing.domain.model.events.VoyageScheduleUpdatedEvent;
import com.example.cargotracker.routing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyageQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyagesQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageListView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueryHandler;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 航海の投影と読み取りモデルを実 DB で固定する。
 *
 * <p>bookingms の {@code CargoProjectionIT} / {@code ShipperProjectionIT} と同じ形。
 * 2 つ目のサービスが同じ型で立ち上がっていることを、検査の側でも揃える。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VoyageProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant DEPART = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-09-24T18:00:00Z");

    @Autowired
    private VoyageProjection projection;

    @Autowired
    private RoutingQueryHandler queries;

    @Autowired
    private AttentionItemMapper attentionItems;

    /**
     * 「今」は本番と同じ時計で決める（BusinessClockConfiguration）。
     *
     * <p>{@code Instant.now()} で作ると、テストだけが JVM 既定の時計を見ることになり、
     * 業務タイムゾーンを変えたときにここだけ食い違う。既定の絞り込み（出港済みを
     * 外す）はクエリ側が同じ時計で判断している。</p>
     */
    @Autowired
    private java.time.Clock clock;

    private static VoyageRegisteredEvent registered(String voyageNumber, List<String> cargoTypes) {
        return new VoyageRegisteredEvent(voyageNumber, "MOL", "商船三井", "MOL EXPRESS",
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "SGSIN", DEPART,
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new VoyageRegisteredEvent.Movement("SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"), ARRIVE)),
                cargoTypes, "routing01");
    }

    /** 航海番号は 20 文字まで。ナノ秒をそのまま繋ぐと桁あふれする。 */
    private static String uniqueNumber() {
        return "V-" + Long.toString(System.nanoTime(), 36);
    }

    @Test
    @DisplayName("航海が投影され、出発地と到着地は最初と最後の移動から決まる")
    void projects() {
        String number = uniqueNumber();

        projection.on(registered(number, List.of("GENERAL")));

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view).isNotNull();
        assertThat(view.vesselName()).isEqualTo("MOL EXPRESS");
        // 非正規化した端点は「最初の出発」と「最後の到着」。途中の寄港地ではない。
        assertThat(view.departureUnLocode()).isEqualTo("JPTYO");
        assertThat(view.arrivalUnLocode()).isEqualTo("USNYC");
        assertThat(view.departureAt()).isEqualTo(DEPART);
        assertThat(view.arrivalAt()).isEqualTo(ARRIVE);
        assertThat(view.movements()).hasSize(2);
        assertThat(view.movements().get(0).movementSeq()).isEqualTo(1);
        assertThat(view.movements().get(1).arrivalUnLocode()).isEqualTo("USNYC");
    }

    @Test
    @DisplayName("同じイベントを 2 度読んでも行は増えない（リプレイの冪等性）")
    void isIdempotent() {
        String number = uniqueNumber();
        VoyageRegisteredEvent event = registered(number, List.of("GENERAL", "HAZARDOUS"));

        projection.on(event);
        projection.on(event);

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view.movements()).hasSize(2);
        assertThat(view.acceptedCargoTypes()).containsExactly("GENERAL", "HAZARDOUS");
    }

    @Test
    @DisplayName("同じ航海番号の 2 件目は UNIQUE で弾かれ、要確認一覧に残る")
    void rejectsDuplicateAndRecordsAttention() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));

        // 別の内容で同じ番号。集約は同時登録のレースで素通りしうるので、
        // ここが最後の砦になる。黙って落とすと「登録したのに一覧に出ない」が残る。
        projection.on(new VoyageRegisteredEvent(number, "ONE", "ONE", "ONE HARMONY",
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "USNYC", DEPART, ARRIVE)),
                List.of("GENERAL"), "routing02"));

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view.vesselName()).as("先に入った行が残る").isEqualTo("MOL EXPRESS");
        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING"))
                .anyMatch(item -> item.targetId().equals(number)
                        && item.reason().equals("航海番号の重複"));
    }

    @Test
    @DisplayName("ヘッダが同じでも受入貨物種別が違えば弾く（黙って広がらない）")
    void rejectsDuplicateThatOnlyDiffersInAcceptedCargoTypes() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));

        // 運送会社も船名も区間も同じ。違うのは「危険物も受ける」と言っている点だけ。
        // ヘッダの行だけを比べるとリプレイに見え、そのまま追記されて
        // 既存の航海が危険物を受け入れることになる。
        projection.on(registered(number, List.of("GENERAL", "HAZARDOUS")));

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view.acceptedCargoTypes())
                .as("2 件目の申告で既存の航海の受入種別が広がってはいけない")
                .containsExactly("GENERAL");
        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING"))
                .anyMatch(item -> item.targetId().equals(number)
                        && item.reason().equals("航海番号の重複"));
    }

    @Test
    @DisplayName("ヘッダが同じでも途中の寄港地が違えば弾く")
    void rejectsDuplicateThatOnlyDiffersInIntermediatePort() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));

        // 端点（JPTYO → USNYC）は同じで、経由地だけが違う。voyage の行は
        // 最初の出発と最後の到着しか持たないので、ヘッダでは見分けられない。
        projection.on(new VoyageRegisteredEvent(number, "MOL", "商船三井", "MOL EXPRESS",
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "HKHKG", DEPART,
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new VoyageRegisteredEvent.Movement("HKHKG", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"), ARRIVE)),
                List.of("GENERAL"), "routing02"));

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view.movements().get(0).arrivalUnLocode())
                .as("先に入った経路が残る").isEqualTo("SGSIN");
        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING"))
                .anyMatch(item -> item.targetId().equals(number)
                        && item.reason().equals("航海番号の重複"));
    }

    @Test
    @DisplayName("弾かれた登録を読み直しても要確認一覧は増えない")
    void attentionItemIsNotDuplicatedOnReplay() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));
        VoyageRegisteredEvent duplicate = new VoyageRegisteredEvent(number, "ONE", "ONE",
                "ONE HARMONY",
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "USNYC", DEPART, ARRIVE)),
                List.of("GENERAL"), "routing02");

        projection.on(duplicate);
        projection.on(duplicate);

        // 識別子を内容から導いているので、読み直しても 1 行のまま。
        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING").stream()
                .filter(item -> item.targetId().equals(number)).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("対応貨物種別で絞ると、受け入れない航海は出ない")
    void filtersByCargoType() {
        String general = uniqueNumber();
        String hazardous = uniqueNumber();
        projection.on(registered(general, List.of("GENERAL")));
        projection.on(registered(hazardous, List.of("GENERAL", "HAZARDOUS")));

        VoyageListView view = queries.handle(
                new FindVoyagesQuery(0, 200, true,
                        VoyageSearchCriteria.of(null, null, null, null, "HAZARDOUS")));

        List<String> numbers = view.items().stream().map(VoyageView::voyageNumber).toList();
        assertThat(numbers).contains(hazardous).doesNotContain(general);
    }

    @Test
    @DisplayName("一覧は既定で出港済みを外す")
    void hidesDepartedByDefault() {
        String departed = uniqueNumber();
        // 出発が過去の航海。既定の一覧に混ざると「これから使える航海」として
        // 一覧全体が信用されなくなる。
        projection.on(new VoyageRegisteredEvent(departed, "MOL", "商船三井", "MOL PAST",
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "USNYC",
                        Instant.parse("2020-01-01T00:00:00Z"),
                        Instant.parse("2020-01-15T00:00:00Z"))),
                List.of("GENERAL"), "routing01"));

        List<String> visible = queries.handle(new FindVoyagesQuery(0, 200, false, null))
                .items().stream().map(VoyageView::voyageNumber).toList();
        List<String> all = queries.handle(new FindVoyagesQuery(0, 200, true, null))
                .items().stream().map(VoyageView::voyageNumber).toList();

        assertThat(visible).doesNotContain(departed);
        assertThat(all).contains(departed);
    }

    private static VoyageRegisteredEvent registeredFrom(String voyageNumber, String from,
            String to, Instant departAt) {
        return new VoyageRegisteredEvent(voyageNumber, "MOL", "商船三井", "MOL EXPRESS",
                List.of(new VoyageRegisteredEvent.Movement(from, to, departAt,
                        departAt.plusSeconds(14 * 24 * 3600))),
                List.of("GENERAL"), "routing01");
    }

    private List<String> search(VoyageSearchCriteria criteria) {
        return queries.handle(new FindVoyagesQuery(0, 200, false, criteria))
                .items().stream().map(VoyageView::voyageNumber).toList();
    }

    @Test
    @DisplayName("更新したあとに登録イベントを読み直しても、重複として記録しない")
    void doesNotReportDuplicateAfterUpdate() {
        // 投影を読み直すと、更新済みの行に対して登録イベントがもう一度届く。
        // 行の中身は更新後のものなので「丸ごと比べる」は一致せず、そのままだと
        // 「航海番号の重複」が偽で積まれる。経路設計者の要確認一覧には、
        // 身に覚えのない警告だけが残る。
        String number = uniqueNumber();
        VoyageRegisteredEvent registration = registered(number, List.of("GENERAL"));
        projection.on(registration);
        projection.on(updated(number, List.of("GENERAL")));

        projection.on(registration);

        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING"))
                .as("読み直しは重複登録ではない")
                .noneSatisfy(item -> assertThat(item.targetId()).isEqualTo(number));
        assertThat(queries.handle(new FindVoyageQuery(number)).vesselName())
                .as("読み直しで更新前の値へ巻き戻さない")
                .isEqualTo("MOL VOYAGER");
    }

    @Test
    @DisplayName("US07: 出発地・目的地で絞れる")
    void filtersByPorts() {
        Instant future = clock.instant().plusSeconds(30 * 24 * 3600);
        String tokyoNewYork = uniqueNumber();
        String tokyoLondon = uniqueNumber();
        projection.on(registeredFrom(tokyoNewYork, "JPTYO", "USNYC", future));
        projection.on(registeredFrom(tokyoLondon, "JPTYO", "GBLON", future));

        assertThat(search(VoyageSearchCriteria.of("JPTYO", "USNYC", null, null, null)))
                .contains(tokyoNewYork).doesNotContain(tokyoLondon);
    }

    @Test
    @DisplayName("US07: 出発期間で絞れる")
    void filtersByDeparturePeriod() {
        Instant soon = clock.instant().plusSeconds(10 * 24 * 3600);
        Instant later = clock.instant().plusSeconds(60 * 24 * 3600);
        String early = uniqueNumber();
        String late = uniqueNumber();
        projection.on(registeredFrom(early, "JPTYO", "USNYC", soon));
        projection.on(registeredFrom(late, "JPTYO", "USNYC", later));

        assertThat(search(VoyageSearchCriteria.of(null, null,
                soon.minusSeconds(3600), soon.plusSeconds(3600), null)))
                .contains(early).doesNotContain(late);
    }

    @Test
    @DisplayName("US07: 検索条件は既定の絞り込みを消さない")
    void searchKeepsDefaultFilter() {
        // 条件で置き換えると、出港済みの航海が検索結果にだけ戻る。一覧では
        // 外しているのに絞り込むと出てくる、という食い違いになる。
        String departed = uniqueNumber();
        projection.on(registeredFrom(departed, "JPTYO", "USNYC",
                Instant.parse("2020-01-01T00:00:00Z")));

        assertThat(search(VoyageSearchCriteria.of("JPTYO", "USNYC", null, null, null)))
                .as("出港済みは条件に合っていても既定では出さない")
                .doesNotContain(departed);
    }

    @Test
    @DisplayName("見つからない航海は null を返す")
    void returnsNullForUnknown() {
        assertThat(queries.handle(new FindVoyageQuery("V-NOT-EXIST"))).isNull();
    }

    private static VoyageScheduleUpdatedEvent updated(String voyageNumber,
            List<String> cargoTypes) {
        // 途中の寄港地を 1 つに減らし、日付も動かす。運航変更はこの形で来る。
        return new VoyageScheduleUpdatedEvent(voyageNumber, "MOL", "商船三井", "MOL VOYAGER",
                List.of(new VoyageScheduleUpdatedEvent.Movement("JPTYO", "USNYC",
                        Instant.parse("2026-09-12T09:00:00Z"),
                        Instant.parse("2026-09-26T18:00:00Z"))),
                cargoTypes, "routing02", Instant.parse("2026-09-05T00:00:00Z"));
    }

    @Test
    @DisplayName("US25: 更新すると寄港地が入れ替わり、最終更新が残る")
    void appliesScheduleUpdate() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));

        projection.on(updated(number, List.of("GENERAL", "HAZARDOUS")));

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view.vesselName()).isEqualTo("MOL VOYAGER");
        assertThat(view.departureAt()).isEqualTo(Instant.parse("2026-09-12T09:00:00Z"));
        assertThat(view.arrivalAt()).isEqualTo(Instant.parse("2026-09-26T18:00:00Z"));
        assertThat(view.movements())
                .as("寄港地は全行を入れ替える。足すだけだと古い区間が残る")
                .hasSize(1);
        assertThat(view.movements().get(0).arrivalUnLocode()).isEqualTo("USNYC");
        assertThat(view.acceptedCargoTypes())
                .as("受入種別も入れ替える。追記だけだと外した種別が残り、"
                        + "対応しない貨物の航海が候補に出る")
                .containsExactly("GENERAL", "HAZARDOUS");
        assertThat(view.updatedAt()).isNotNull();
        assertThat(view.updatedBy()).isEqualTo("routing02");
    }

    @Test
    @DisplayName("受入種別を減らす更新でも、外した種別は残らない")
    void narrowsAcceptedCargoTypes() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL", "HAZARDOUS")));

        projection.on(updated(number, List.of("GENERAL")));

        assertThat(queries.handle(new FindVoyageQuery(number)).acceptedCargoTypes())
                .containsExactly("GENERAL");
    }

    @Test
    @DisplayName("同じ更新を 2 度読んでも行は増えない（リプレイの冪等性）")
    void updateIsIdempotent() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));
        VoyageScheduleUpdatedEvent event = updated(number, List.of("GENERAL"));

        projection.on(event);
        projection.on(event);

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view.movements()).hasSize(1);
        assertThat(view.acceptedCargoTypes()).containsExactly("GENERAL");
    }

    @Test
    @DisplayName("投影に無い航海の更新は黙って捨てない")
    void recordsUpdateWithoutRow() {
        String number = uniqueNumber();

        projection.on(updated(number, List.of("GENERAL")));

        assertThat(queries.handle(new FindVoyageQuery(number)))
                .as("行を作らない。登録を経ていない航海が投影にだけ生まれる")
                .isNull();
        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING"))
                .as("黙って捨てると、更新したのに反映されないことが誰にも見えない")
                .anySatisfy(item -> assertThat(item.targetId()).isEqualTo(number));
    }

    @Test
    @DisplayName("R.1: キャンセルすると理由と日時が読める（記録と読み口を対で出す）")
    void projectsCancellation() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));

        projection.on(new VoyageCancelledEvent(number, "運航中止", "routing01"));

        VoyageView view = queries.handle(new FindVoyageQuery(number));
        assertThat(view.cancelled()).isTrue();
        assertThat(view.cancelReason()).isEqualTo("運航中止");
        assertThat(view.cancelledBy()).isEqualTo("routing01");
        assertThat(view.cancelledAt()).isNotNull();
        // 止めた航海の「元の予定」は残す。上書きすると何を止めたのかが読めなくなる。
        assertThat(view.departureUnLocode()).isEqualTo("JPTYO");
        assertThat(view.movements()).hasSize(2);
    }

    @Test
    @DisplayName("R.1: キャンセルした航海は一覧の既定から外れる")
    void hidesCancelledFromList() {
        String number = uniqueNumber();
        projection.on(registered(number, List.of("GENERAL")));

        projection.on(new VoyageCancelledEvent(number, "運航中止", "routing01"));

        assertThat(queries.handle(new FindVoyagesQuery(0, 200, false,
                VoyageSearchCriteria.of(null, null, null, null, null)))
                .items().stream().map(VoyageView::voyageNumber))
                .doesNotContain(number);
    }

    @Test
    @DisplayName("R.1: 投影に無い航海のキャンセルは要確認一覧に残る（黙らない）")
    void recordsAttentionWhenCancellingUnknownVoyage() {
        String number = uniqueNumber();

        projection.on(new VoyageCancelledEvent(number, "運航中止", "routing01"));

        assertThat(attentionItems.findOpenByRole("ROLE_ROUTING"))
                .anyMatch(item -> item.targetId().equals(number)
                        && item.reason().equals("キャンセルの対象が投影に無い"));
    }
}
