package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.TrackingNumber;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 経路と確定が実際の DB で成立する（旅程・通知の記録・追跡番号）。
 *
 * <p>予約そのもの（{@link CargoPersistenceIntegrationTest}）と分けたのは、確かめている
 * ものが違うからである。ここで見るのは<strong>予約に後から足されるもの</strong>で、
 * 差し替えたときに行が増えないことまでを含む。
 */
@DisplayName("経路と確定の永続化")
class CargoItineraryPersistenceIntegrationTest extends CargoPersistenceTestBase {

    @Test
    @DisplayName("旅程が保存され、区間が順序どおりに読み戻せる")
    void persistsItinerary() {
        Cargo booked = bookCargo.book(command(shipperId("旅程太郎", "itinerary@example.com"),
                CargoType.GENERAL));
        Cargo assigned = repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));

        Cargo found = repository.findById(assigned.id()).orElseThrow();

        assertThat(found.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
        assertThat(found.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
        assertThat(found.itinerary()).isPresent();
        // 順序に意味がある。並びが崩れると「東京 → ロサンゼルス → 上海」になる
        assertThat(found.itinerary().orElseThrow().legs())
                .extracting(leg -> leg.loadLocation().unLocode())
                .containsExactly("JPTYO", "CNSHA");
        // 地点は名称まで読み戻す。画面がコードから引き直さずに済む
        assertThat(found.itinerary().orElseThrow().legs().get(0).unloadLocation().name())
                .isEqualTo("Shanghai");
        assertThat(found.itinerary().orElseThrow().expectedArrivalTime())
                .isEqualTo(Instant.parse("2030-09-18T09:00:00Z"));
    }

    /**
     * IT3 の欠陥と同じ形。区間を消さずに入れ直すと、旅程が二重になる。
     *
     * <p>しかも順序は保たれるため、画面上は「区間が増えた」ようにしか見えない。
     */
    @Test
    @DisplayName("経路を差し替えても区間の行が増えない")
    void replacingItineraryDoesNotAddRows() {
        Cargo booked = bookCargo.book(command(shipperId("差替太郎", "replace@example.com"),
                CargoType.GENERAL));
        Cargo assigned = repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));

        Cargo replaced = repository.save(
                assigned.assignItinerary(itineraryVia("SGSIN", "Singapore"), LA));

        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM leg WHERE cargo_id = ?", Long.class, assigned.id());
        assertThat(rows).as("差し替えで区間の行が増えている").isEqualTo(2L);
        // **保存の戻り値ではなく DB から読み戻す。**戻り値は渡した集約そのものなので、
        // 古い区間を消して古い区間を入れ直す実装でも緑になる
        assertThat(repository.findById(assigned.id()).orElseThrow().itinerary().orElseThrow()
                .legs())
                .extracting(leg -> leg.unloadLocation().unLocode())
                .containsExactly("SGSIN", "USLAX");
        assertThat(replaced.itinerary()).isPresent();
    }

    @Test
    @DisplayName("経路が決まっていない予約は旅程を持たない")
    void cargoWithoutItineraryReadsBack() {
        Cargo booked = bookCargo.book(command(shipperId("未定太郎", "no-itinerary@example.com"),
                CargoType.GENERAL));

        // 空のリストと「旅程が無い」を取り違えると、画面が空の旅程表を出す
        assertThat(repository.findById(booked.id()).orElseThrow().itinerary()).isEmpty();
    }

    /**
     * 通知の記録と追跡番号が<strong>読み戻せる</strong>（US12-4・US14）。
     *
     * <p>戻り値だけを見ると、列に書いていない実装でも通る（IT6 タスク 0.9 で直した形と同じ）。
     */
    @Test
    @DisplayName("通知の記録と追跡番号が読み戻せる")
    void persistsNotificationAndTrackingNumber() {
        Cargo booked = bookCargo.book(command(shipperId("通知太郎", "cargo-notify@example.com"),
                CargoType.GENERAL));
        String bookingId = booked.bookingId().orElseThrow().value();

        Cargo routed = repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));

        Instant notifiedAt = Instant.parse("2026-08-22T02:00:00Z");
        Cargo confirmed = repository.save(
                repository.save(routed.notifyShipper(notifiedAt, "sales01")).confirm());

        // 採番は本番と同じ経路（シーケンス）を通す。自前採番だと UNIQUE 制約で落ちる
        String number = repository.nextTrackingNumber();
        assertThat(number).matches("^TRK-\\d{8}-\\d{4}$");
        repository.save(confirmed.issueTrackingNumber(TrackingNumber.of(number)));

        assertThat(repository.findByBookingId(bookingId))
                .get()
                .satisfies(found -> {
                    Cargo cargo = found.cargo();
                    assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
                    assertThat(cargo.routeNotification().orElseThrow().notifiedAt())
                            .isEqualTo(notifiedAt);
                    assertThat(cargo.routeNotification().orElseThrow().notifiedBy())
                            .isEqualTo("sales01");
                    assertThat(cargo.trackingNumber().orElseThrow().value()).isEqualTo(number);
                });
    }

    /** 採番は続けて呼んでも衝突しない（US14-2）。 */
    @Test
    @DisplayName("追跡番号は続けて採番しても衝突しない")
    void numbersDistinctTrackingNumbers() {
        assertThat(repository.nextTrackingNumber())
                .isNotEqualTo(repository.nextTrackingNumber());
    }

    /**
     * <strong>発行はトランザクションの中で行われる</strong>（[ADR-022] 決定 6）。
     *
     * <p>「コミット後に送る」機構は、送るときにトランザクションが生きていて初めて働く。
     * 置き忘れると機構は素通りし、結果の順序が正しいのは「たまたま save のあとに呼んで
     * いる」からになる。<strong>本番の呼び出し形で同期が有効であること</strong>を固定する。
     *
     * <p>アダプタ側の「コミット前は送らない・ロールバックでは送らない」は
     * {@code RabbitCargoEventNotifierTest} が見る。ここが見るのは<strong>境界の有無</strong>である。
     */
    @Test
    @DisplayName("追跡番号の発行は、トランザクションの中から伝える")
    void publishesInsideATransaction() {
        Cargo booked = bookCargo.book(command(shipperId("発行太郎", "cargo-issue-tx@example.com"),
                CargoType.GENERAL));
        String bookingId = booked.bookingId().orElseThrow().value();
        Cargo confirmed = repository.save(repository.save(repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA))
                .notifyShipper(java.time.Instant.parse("2026-08-22T02:00:00Z"), "sales01"))
                .confirm());
        assertThat(confirmed.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);

        issueTrackingNumber.issue(bookingId);

        assertThat(transactionActiveWhenPublished)
                .as("発行の時点でトランザクションが生きていない。"
                        + "「コミット後に送る」機構が素通りしている")
                .isTrue();
    }

    /**
     * 追跡番号の日付は<strong>業務タイムゾーン</strong>で決まる（IT6 のクローズレビュー）。
     *
     * <p>`CURRENT_DATE` は DB のセッションのタイムゾーン（コンテナは通常 UTC）で決まる。
     * それを使うと、<strong>日本時間の 00:00〜09:00 に発行した番号が前日の日付を持つ</strong>。
     * 「番号だけでいつごろの貨物か分かる」という目的が 1 日 9 時間ぶん外れる。
     *
     * <p><strong>テストも同じ Clock で「今日」を決める。</strong>ここで
     * {@code LocalDate.now()} を書くと、CI（UTC）でだけ落ちるテストになる。
     */
    @Test
    @DisplayName("追跡番号の日付は業務タイムゾーンの今日")
    void numbersWithTheBusinessDate() {
        String expected = java.time.LocalDate.now(clock)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        assertThat(repository.nextTrackingNumber()).startsWith("TRK-" + expected + "-");
    }

    /**
     * 追跡番号から貨物を引く（US15-1・[ADR-023] 決定 2）。
     *
     * <p><strong>旅程まで戻ることを見る。</strong>join を書き落として旅程が空になると、
     * handlingms 側の照合は「照らす相手が無い」として<strong>すべての積込・荷降しを
     * 予定外に倒す</strong>（安全側に倒す設計なので、例外にはならない）。
     * 荷役の記録すべてに「予定外」が付き、しかもどこにもエラーが出ない。
     *
     * <p>方言スモークは解釈できるかしか見ないため、この形は捕まえられない。
     */
    @Test
    @DisplayName("追跡番号から貨物を引くと、旅程まで戻る")
    void findsCargoByTrackingNumberWithItinerary() {
        Cargo booked = repository.save(bookCargo.book(command(
                shipperId("追跡照会太郎", "cargo-by-tracking@example.com"), CargoType.GENERAL)));
        BookingId bookingId = booked.bookingId().orElseThrow();
        Cargo routed = repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));
        Cargo issued = repository.save(routed
                .notifyShipper(Instant.parse("2026-08-22T02:00:00Z"), "sales01")
                .confirm()
                .issueTrackingNumber(TrackingNumber.of(repository.nextTrackingNumber())));
        String trackingNumber = issued.trackingNumber().orElseThrow().value();

        Cargo found = repository.findByTrackingNumber(trackingNumber).orElseThrow().cargo();

        assertThat(found.bookingId()).contains(bookingId);
        assertThat(found.itinerary().orElseThrow().legs())
                .as("旅程が戻らない。荷役の照合が、すべての作業を予定外に倒す")
                .hasSize(2);
        assertThat(found.itinerary().orElseThrow().legs().getFirst().loadLocation().unLocode())
                .isEqualTo("JPTYO");
    }

    @Test
    @DisplayName("知らない追跡番号では空を返す")
    void findsNothingForUnknownTrackingNumber() {
        assertThat(repository.findByTrackingNumber("TRK-99999999-9999")).isEmpty();
    }

}
