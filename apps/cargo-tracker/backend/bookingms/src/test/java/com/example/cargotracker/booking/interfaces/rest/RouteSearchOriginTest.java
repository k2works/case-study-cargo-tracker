package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.RouteConditionView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 経路探索の起点（US28 §受入基準 4・5）。
 *
 * <p><b>US28 の中核がこの 1 つの分岐にある。</b> 誤配のときは調整済みの条件より
 * 現在地を優先する——ここを消しても、候補算出の統合テストは緑のままになる
 * （IT11 レビューで tester が指摘）。</p>
 */
class RouteSearchOriginTest {

    private static BookingView booking(String routingStatus, String lastHandlingUnLocode) {
        return new BookingView("b-1", "B-2026-0902-004", "SHP-000001", "山田商事",
                "JPTYO", "USNYC", LocalDate.of(2026, Month.OCTOBER, 15), "GENERAL",
                null, null, null, null, 10, "自動車部品", null, null, null, null,
                "ROUTE_PROPOSED", routingStatus, Instant.parse("2026-09-02T01:00:00Z"),
                null, null, null, null, null, null, null, null,
                List.of(), null, null, null, null, null,
                lastHandlingUnLocode, Instant.parse("2026-09-28T00:30:00Z"), true,
                null, null);
    }

    private static RouteConditionView condition(String departFrom) {
        return new RouteConditionView(List.of(), departFrom);
    }

    @Test
    @DisplayName("US28 §4: 誤配なら現在地を起点にする（調整済みの条件より優先）")
    void usesTheCurrentLocationWhenMisrouted() {
        // **条件任せにすると、経路設計者が毎回手で現在地を入れ直すことになる。**
        // 入れ忘れれば、貨物のいない港から探した経路が出る。
        var from = BookingController.departFrom(
                booking("MISROUTED", "SGSIN"), condition("JPOSA"));

        assertThat(from).isNotNull();
        assertThat(from.unLocode().value()).isEqualTo("SGSIN");
    }

    @Test
    @DisplayName("US28 §5: 誤配でなければ調整済みの条件に従う（緩めすぎに気づける）")
    void keepsTheAdjustedConditionWhenNotMisrouted() {
        var from = BookingController.departFrom(
                booking("ROUTING_REQUESTED", "SGSIN"), condition("JPOSA"));

        assertThat(from).isNotNull();
        assertThat(from.unLocode().value()).isEqualTo("JPOSA");
    }

    @Test
    @DisplayName("条件も現在地も無ければ、起点を指定しない（これまでどおりの探索）")
    void leavesTheOriginUnsetWhenNothingIsKnown() {
        assertThat(BookingController.departFrom(
                booking("ROUTING_REQUESTED", null), condition(null))).isNull();
    }

    @Test
    @DisplayName("誤配でも現在地が分からなければ、条件に従う（列が無かったころの行）")
    void fallsBackToTheConditionWhenTheLocationIsUnknown() {
        // **不変条件の追加は既存行を壊す。** 誤配の記録に港が無い予約でも読める。
        var from = BookingController.departFrom(
                booking("MISROUTED", null), condition("JPOSA"));

        assertThat(from).isNotNull();
        assertThat(from.unLocode().value()).isEqualTo("JPOSA");
    }
}
