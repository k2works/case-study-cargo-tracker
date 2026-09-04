package com.example.cargotracker.routing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
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

    private static VoyageRegisteredEvent registered(String voyageNumber, List<String> cargoTypes) {
        return new VoyageRegisteredEvent(voyageNumber, "MOL", "商船三井", "MOL EXPRESS",
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "SGSIN", DEPART,
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new VoyageRegisteredEvent.Movement("SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"), ARRIVE)),
                cargoTypes, "routing01");
    }

    private static String uniqueNumber() {
        return ("V-" + System.nanoTime());
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
                new FindVoyagesQuery(0, 200, true, "HAZARDOUS"));

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

    @Test
    @DisplayName("見つからない航海は null を返す")
    void returnsNullForUnknown() {
        assertThat(queries.handle(new FindVoyageQuery("V-NOT-EXIST"))).isNull();
    }
}
