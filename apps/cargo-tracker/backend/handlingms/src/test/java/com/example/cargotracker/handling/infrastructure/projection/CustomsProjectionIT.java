package com.example.cargotracker.handling.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.domain.model.events.CustomsDeclarationRegisteredEvent;
import com.example.cargotracker.handling.domain.model.events.CustomsStatusUpdatedEvent;
import com.example.cargotracker.handling.infrastructure.query.CustomsQueryHandler;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CustomsDeclarationView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsDeclarationsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCustomsHistoryQuery;
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
                // 既定は日本の輸入港。営業日の期待値は日本の暦で書いてある
                // （8/11 の山の日を外す）。国を変える検査だけが明示的に上書きする。
                "b-" + suffix, "JPTYO", DECLARED, "handler01", DECLARED),
                "evt-" + System.nanoTime());
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
    @DisplayName("US29 §6: 督促の対象だけに絞れる（S02 の件数もこの一覧から数える）")
    void narrowsToOverdue() {
        String overdue = register("O1");
        update(overdue, "PENDING", "HELD", "検査待ち", Instant.parse("2026-08-10T02:00:00Z"));

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, null, null, true)))
                .contains(overdue);
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
        var event = new CustomsDeclarationRegisteredEvent(number, "TRK-IDEM", "b-idem", "USNYC",
                DECLARED, "handler01", DECLARED);

        projection.on(event, "evt-idem");
        projection.on(event, "evt-idem");

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, "TRK-IDEM", null, false)))
                .containsOnlyOnce(number);
        // **履歴も見る**（IT12 レビュー 中）。追記の表なので、主キーが元イベントの
        // 識別子でなければリプレイで行が積み上がる（IT6 で実際に踏んだ形）。
        // §8 の乖離記録は「積み上がらない」と**保証として書いている**。
        assertThat(queries.handle(new FindCustomsHistoryQuery(number)).items())
                .as("同じイベントを 2 度流しても履歴は 1 行")
                .hasSize(1);
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

    @Test
    @DisplayName("不変条件 3: 同じ貨物に未決着の申告は 2 件残らない（DB が断る）")
    void keepsAtMostOneUnsettledDeclarationPerCargo() {
        // **画面の確認だけでは同時の 2 件が通る。** 登録は投影を読んでから
        // コマンドを送るので、2 つの要求が同時に来ると両方とも「未決着は無い」を
        // 見る。**部分ユニークインデックスが最後の砦**（IT12 レビュー #L15）。
        String cargo = "TRK-UNQ-" + System.nanoTime();
        String first = "IMP-UNQ1-" + System.nanoTime();
        String second = "IMP-UNQ2-" + System.nanoTime();

        projection.on(new CustomsDeclarationRegisteredEvent(first, cargo, "b-unq", "USNYC",
                DECLARED, "handler01", DECLARED), "evt-" + System.nanoTime());
        projection.on(new CustomsDeclarationRegisteredEvent(second, cargo, "b-unq", "USNYC",
                DECLARED, "handler01", DECLARED), "evt-" + System.nanoTime());

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, cargo, null, false)))
                .as("2 件目は残らない")
                .containsExactly(first);
    }

    @Test
    @DisplayName("決着したあとなら同じ貨物に出し直せる")
    void allowsRedeclarationAfterSettlement() {
        // **不可のあとは出し直せる。** 部分ユニークは未決着だけを見ているので、
        // 決着した申告は 2 件目を妨げない。
        String cargo = "TRK-RED-" + System.nanoTime();
        String first = "IMP-RED1-" + System.nanoTime();
        String second = "IMP-RED2-" + System.nanoTime();

        projection.on(new CustomsDeclarationRegisteredEvent(first, cargo, "b-red", "USNYC",
                DECLARED, "handler01", DECLARED), "evt-" + System.nanoTime());
        projection.on(new CustomsStatusUpdatedEvent(first, "PENDING", "REJECTED",
                "書類不備", "tracker01", DECLARED.plusSeconds(3600)), "evt-" + System.nanoTime());
        projection.on(new CustomsDeclarationRegisteredEvent(second, cargo, "b-red", "USNYC",
                DECLARED.plusSeconds(7200), "handler01", DECLARED.plusSeconds(7200)),
                "evt-" + System.nanoTime());

        assertThat(numbersOf(new FindCustomsDeclarationsQuery(true, cargo, null, false)))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("#L17: 留置の営業日は輸入港の国の暦で数える（米国の独立記念日）")
    void countsBusinessDaysInTheImportCountry() {
        // **日本固定だと、HolidayCalendar の国別分岐は本番で一度も踏まれない。**
        // 7/3（金）に留置 → 7/8（水）。米国では 7/4 が休日だが振替は無く、
        // 7/4 は土曜。日本では 7 月に固定休日が無い。**判別できる日を選ぶ**——
        // 2026-07-03(金) → 2026-07-13(月) で数えると、米国は 7 営業日
        // （7/4 土・7/5 日・7/11 土・7/12 日を外す）、日本も 7 営業日で同じになる。
        // 国の違いが出るのは日本の固定休日を含む区間なので、そちらで見る:
        // 2026-08-07(金) → 2026-08-14(金)。日本は 8/11（山の日）を外して 4 営業日、
        // 米国は 5 営業日。
        String usCargo = "TRK-US-" + System.nanoTime();
        String usNumber = "IMP-US-" + System.nanoTime();
        projection.on(new CustomsDeclarationRegisteredEvent(usNumber, usCargo, "b-us", "USNYC",
                DECLARED, "handler01", DECLARED), "evt-" + System.nanoTime());
        update(usNumber, "PENDING", "HELD", "検査待ち", Instant.parse("2026-08-07T02:00:00Z"));
        update(usNumber, "HELD", "CLEARED", "解除", Instant.parse("2026-08-14T02:00:00Z"));

        String jpCargo = "TRK-JP-" + System.nanoTime();
        String jpNumber = "IMP-JP-" + System.nanoTime();
        projection.on(new CustomsDeclarationRegisteredEvent(jpNumber, jpCargo, "b-jp", "JPTYO",
                DECLARED, "handler01", DECLARED), "evt-" + System.nanoTime());
        update(jpNumber, "PENDING", "HELD", "検査待ち", Instant.parse("2026-08-07T02:00:00Z"));
        update(jpNumber, "HELD", "CLEARED", "解除", Instant.parse("2026-08-14T02:00:00Z"));

        assertThat(queries.handle(new FindCustomsDeclarationsQuery(true, usCargo, null, false))
                .items()).singleElement()
                .satisfies(view -> assertThat(view.heldBusinessDays())
                        .as("米国の暦では 8/11 は休日ではない")
                        .isEqualTo(5));
        assertThat(queries.handle(new FindCustomsDeclarationsQuery(true, jpCargo, null, false))
                .items()).singleElement()
                .satisfies(view -> assertThat(view.heldBusinessDays())
                        .as("日本の暦では 8/11（山の日）を外す")
                        .isEqualTo(4));
    }

    @Test
    @DisplayName("#L18: 記録した営業日数と、読み取りが数える営業日数が一致する")
    void theRecordedAndTheReadCountAgree() {
        // **請求（US21）は記録側の値を根拠にし、画面は読み取り側を出す。**
        // 2 つが違うと、経理が見た日数と請求の根拠が食い違う。
        String cargo = "TRK-AGREE-" + System.nanoTime();
        String number = "IMP-AGREE-" + System.nanoTime();
        projection.on(new CustomsDeclarationRegisteredEvent(number, cargo, "b-agree", "JPTYO",
                DECLARED, "handler01", DECLARED), "evt-" + System.nanoTime());
        Instant heldAt = Instant.parse("2026-08-07T02:00:00Z");
        Instant clearedAt = Instant.parse("2026-08-14T02:00:00Z");
        update(number, "PENDING", "HELD", "検査待ち", heldAt);
        update(number, "HELD", "CLEARED", "解除", clearedAt);

        int recorded = com.example.cargotracker.shared.domain.calendar.HolidayCalendar
                .of(new com.example.cargotracker.shared.domain.location.UnLocode("JPTYO")
                        .countryCode())
                .businessDaysBetween(
                        java.time.LocalDate.ofInstant(heldAt, java.time.ZoneId.of("Asia/Tokyo")),
                        java.time.LocalDate.ofInstant(clearedAt,
                                java.time.ZoneId.of("Asia/Tokyo")));

        assertThat(queries.handle(new FindCustomsDeclarationsQuery(true, cargo, null, false))
                .items()).singleElement()
                .satisfies(view -> assertThat(view.heldBusinessDays())
                        .as("記録の時点で載せた日数と、読み取りが数える日数は同じでなければならない")
                        .isEqualTo(recorded));
    }
}
