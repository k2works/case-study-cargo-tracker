package com.example.cargotracker.handling.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.domain.model.events.CustomsDeclarationRegisteredEvent;
import com.example.cargotracker.handling.domain.model.events.CustomsStatusUpdatedEvent;
import com.example.cargotracker.handling.infrastructure.query.CustomsQueryHandler;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CountOverdueCustomsHoldsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsDeclarationView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsDeclarationsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsStatusOfCargoQuery;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 通関申告の投影と読み取り（US29 §受入基準 6・7）。
 *
 * <p>集約の検査は「集約が何を許すか」を見るもので、<b>一覧がどう見えるか</b>は
 * 判別しない。ここでは実際の PostgreSQL に書いて読み直す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomsProjectionIT extends AbstractAxonIntegrationTest {

    /** 申告日時は<b>過去</b>にする。更新は申告より後に起きるので、未来だと順序が逆になる。 */
    private static final Instant DECLARED = Instant.parse("2026-08-03T09:00:00Z");

    @Autowired
    private CustomsDeclarationProjection projection;

    @Autowired
    private CustomsQueryHandler queries;

    private String register(String suffix) {
        String number = "IMP-" + suffix + "-" + System.nanoTime();
        projection.on(new CustomsDeclarationRegisteredEvent(number, "TRK-" + suffix,
                "b-" + suffix, DECLARED, "handler01", DECLARED), "evt-" + System.nanoTime());
        return number;
    }

    private void update(String number, String from, String to, String reason, Instant at) {
        projection.on(new CustomsStatusUpdatedEvent(number, from, to, reason, "tracker01", at),
                "evt-" + System.nanoTime());
    }

    private List<String> numbersOf(FindCustomsDeclarationsQuery query) {
        return queries.handle(query).items().stream()
                .map(CustomsDeclarationView::declarationNumber).toList();
    }

    @Test
    @DisplayName("US29 §1: 登録すると審査中で一覧に出る")
    void listsNewDeclarationAsPending() {
        String number = register("P1");

        assertThat(queries.handle(new FindCustomsDeclarationsQuery(false, null, null, false))
                .items())
                .filteredOn(view -> view.declarationNumber().equals(number))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.status()).isEqualTo("PENDING");
                    assertThat(view.statusLabel()).isEqualTo("審査中");
                });
    }

    @Test
    @DisplayName("US29 §7: 既定で通関済を外し、切り替えると出る")
    void excludesClearedByDefault() {
        // **決着したものが混ざると、一覧全体が「まだ手を入れる場所」に見えなくなる。**
        String number = register("C1");
        update(number, "PENDING", "CLEARED", "書類に不備なし", DECLARED.plusSeconds(3600));

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(false, null, null, false)))
                .doesNotContain(number);
        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, null, null, false)))
                .contains(number);
    }

    @Test
    @DisplayName("US29 §7: 追跡番号と通関状態で絞れる")
    void filtersByCargoAndStatus() {
        String held = register("F1");
        update(held, "PENDING", "HELD", "検査待ち", DECLARED.plusSeconds(3600));
        String pending = register("F2");

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, "TRK-F1", null, false)))
                .contains(held).doesNotContain(pending);
        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, null, "HELD", false)))
                .contains(held).doesNotContain(pending);
    }

    @Test
    @DisplayName("不変条件 4: 留置中の営業日数は読むときに数える（列は古くならない）")
    void countsHeldBusinessDaysAtReadTime() {
        // **投影に持つと古くなる。** 留置中は日が経つだけで日数が変わるのに、
        // イベントは来ない。ここでは十分に古い留置を作って、0 でないことを見る。
        String number = register("H1");
        update(number, "PENDING", "HELD", "検査待ち", Instant.parse("2026-08-10T02:00:00Z"));

        assertThat(queries.handle(new FindCustomsDeclarationsQuery(true, "TRK-H1", null, false))
                .items())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.heldBusinessDays())
                            .as("列の値をそのまま返すと 0 のままになる")
                            .isGreaterThan(3);
                    assertThat(view.overdue()).isTrue();
                });
    }

    @Test
    @DisplayName("不変条件 4: 留置から出たあとも営業日数が残る（料金調整の根拠）")
    void keepsHeldBusinessDaysAfterLeavingHeld() {
        // **列を返すと 0 になる。** 投影は内部イベントだけを読むので、確定値を
        // 持つ契約イベントから写す相手がいない。5 営業日留置されて通関済に
        // なった申告が「留置 0 営業日」に見えると、US21 の調整根拠が消える。
        String number = register("K1");
        update(number, "PENDING", "HELD", "検査待ち",
                Instant.parse("2026-08-10T02:00:00Z"));
        update(number, "HELD", "CLEARED", "証明書を受領",
                Instant.parse("2026-08-17T02:00:00Z"));

        assertThat(queries.handle(new FindCustomsDeclarationsQuery(true, "TRK-K1", null, false))
                .items())
                .singleElement()
                .satisfies(view -> {
                    // 2026-08-10(月) → 2026-08-17(月)。土日と 8/11（山の日）を
                    // 外して 4 営業日。**暦日なら 7 日**——休日を数えないことが
                    // ここでも効いている。
                    assertThat(view.heldBusinessDays()).isEqualTo(4);
                    // **決着しているので督促の対象ではない。** 日数だけが残る。
                    assertThat(view.overdue()).isFalse();
                });
    }

    @Test
    @DisplayName("US29 §6: 督促の対象だけに絞れて、件数も同じ判定で数える")
    void narrowsToOverdueAndCountsTheSameWay() {
        String overdue = register("O1");
        update(overdue, "PENDING", "HELD", "検査待ち", Instant.parse("2026-08-10T02:00:00Z"));

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, null, null, true)))
                .contains(overdue);
        // **件数は次の行動へ繋ぐ。** 一覧と違う判定で数えると、
        // 件数をたどった先に何も無い、が起きる。
        assertThat(queries.handle(new CountOverdueCustomsHoldsQuery()))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("督促の対象が一覧の先頭に来る（留置営業日の多い順）")
    void putsTheMostOverdueFirst() {
        String older = register("S1");
        update(older, "PENDING", "HELD", "検査待ち", Instant.parse("2026-08-10T02:00:00Z"));
        String newer = register("S2");
        update(newer, "PENDING", "HELD", "検査待ち", Instant.parse("2026-09-07T02:00:00Z"));

        List<String> order = numbersOf(new FindCustomsDeclarationsQuery(true, null, "HELD", false))
                .stream().filter(number -> number.equals(older) || number.equals(newer)).toList();

        assertThat(order).containsExactly(older, newer);
    }

    @Test
    @DisplayName("引取のガードは貨物から最新の申告を引ける（無ければ null）")
    void findsTheLatestDeclarationOfCargo() {
        // **「無い」と「審査中」は違う。** 前者はまだ申告していない、
        // 後者は申告して審査を待っている。
        assertThat(queries.handle(new FindCustomsStatusOfCargoQuery("TRK-NONE-" + System.nanoTime())))
                .isNull();

        String number = register("G1");
        update(number, "PENDING", "CLEARED", "不備なし", DECLARED.plusSeconds(3600));

        assertThat(queries.handle(new FindCustomsStatusOfCargoQuery("TRK-G1")))
                .isNotNull()
                .satisfies(view -> assertThat(view.status()).isEqualTo("CLEARED"));
    }

    @Test
    @DisplayName("同じイベントを 2 度読んでも行は増えない")
    void isIdempotent() {
        String number = "IMP-IDEM-" + System.nanoTime();
        var event = new CustomsDeclarationRegisteredEvent(number, "TRK-IDEM", "b-idem",
                DECLARED, "handler01", DECLARED);

        projection.on(event, "evt-idem");
        projection.on(event, "evt-idem");

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, "TRK-IDEM", null, false)))
                .containsOnlyOnce(number);
    }

    @Test
    @DisplayName("古い更新が遅れて届いても巻き戻らない")
    void doesNotRewindOnLateDelivery() {
        // **少なくとも 1 回配送では起こりうる順序。** 留置 → 通関済のあとに
        // 留置がもう一度届くと、決着したものが未決着に戻って見える。
        String number = register("L1");
        update(number, "PENDING", "HELD", "検査待ち", DECLARED.plusSeconds(3600));
        update(number, "HELD", "CLEARED", "証明書を受領", DECLARED.plusSeconds(7200));

        update(number, "PENDING", "HELD", "検査待ち", DECLARED.plusSeconds(3600));

        assertThat(queries.handle(new FindCustomsStatusOfCargoQuery("TRK-L1")).status())
                .isEqualTo("CLEARED");
    }
}
