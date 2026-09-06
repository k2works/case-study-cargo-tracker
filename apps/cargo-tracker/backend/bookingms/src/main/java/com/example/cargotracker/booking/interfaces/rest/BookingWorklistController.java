package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingConfirmationListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ConditionReviewListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountAwaitingNotificationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountBookingsByStatusQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindAwaitingConfirmationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindConditionReviewsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import java.util.Map;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ロールごとの「受け皿」と件数（S02・S30）。
 *
 * <p>{@link BookingController} から切り出した。<b>予約 1 件を扱う経路とは関心が違う</b>
 * ——こちらは「その人がいま何をすべきか」を集める読み口で、対象は複数件である。</p>
 *
 * <p><b>件数だけを返さない読み口がある。</b> 件数では、どの予約を開けばよいかが
 * 分からない（IT4 の「気づく手段は次の行動へ繋ぐ」）。</p>
 *
 * <p>経路は {@code /bookings} 配下のままにする。<b>予約の読み口だからである。</b>
 * Spring は {@code /{bookingId}} のような変数より literal な経路を先に選ぶので、
 * 別のクラスに分けても当たり方は変わらない。</p>
 */
@RestController
@RequestMapping("/api/v1/booking/bookings")
public class BookingWorklistController {

    private final QueryDispatcher queries;

    public BookingWorklistController(QueryDispatcher queries) {
        this.queries = queries;
    }

    /**
     * 見直しを頼まれている予約（S02 / 営業。US10 §受入基準 4）。
     *
     * <p>件数でなく行を返す。理由が読めないと、営業は荷主と何を協議すればよいのか
     * 分からない（IT4 の「気づく手段は次の行動へ繋ぐ」）。</p>
     */
    @GetMapping("/condition-reviews")
    public ResponseEntity<ConditionReviewListView> conditionReviews(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(queries.query(
                new FindConditionReviewsQuery(limit), ConditionReviewListView.class));
    }

    /**
     * 確定を待っている予約（S02 / 営業。US13 §受入基準 3）。
     *
     * <p><b>件数でなく行を返す。</b> 件数だけでは、営業はどの予約を開けばよいか
     * 分からない（IT4 の「気づく手段は次の行動へ繋ぐ」）。</p>
     */
    @GetMapping("/awaiting-confirmation")
    public ResponseEntity<AwaitingConfirmationListView> awaitingConfirmation(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(queries.query(
                new FindAwaitingConfirmationQuery(limit), AwaitingConfirmationListView.class));
    }

    /**
     * 経路設計作業一覧（S30）。
     *
     * <p>routingms ではなくここに置く。{@code routing_read_db} に予約の表は無く、
     * 一覧のために写しも作らない（写しを作ると Booking の状態と二重管理になる）。
     * 経路設計ロールへの開放は Gateway のルートとロールで行う。</p>
     */
    @GetMapping("/routing-worklist")
    public ResponseEntity<BookingListView> routingWorklist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "false") boolean includeRouted) {
        return ResponseEntity.ok(queries.query(
                new FindRoutingWorklistQuery(page, size, includeRouted), BookingListView.class));
    }

    /**
     * ダッシュボード（S02）の「今日の作業」の件数。
     *
     * <p>ロールごとに見るものが違う。{@code preliminary}（まだ引き渡していない予約）は
     * <b>営業の仕事</b>で、{@code routingWorklist}（設計待ち・誤配）は経路設計者の仕事。
     * 経路設計者に {@code preliminary} を出しても、その件数に対して打てる手が無い。</p>
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Integer>> summary() {
        BookingListView worklist = queries.query(new FindRoutingWorklistQuery(0, 1, false),
                BookingListView.class);
        return ResponseEntity.ok(Map.of(
                "preliminary",
                queries.query(new CountBookingsByStatusQuery(BookingStatus.PRELIMINARY.name()),
                        Integer.class),
                "routingWorklist", worklist.total(),
                // 荷主へ通知していない経路確定済みの予約（US12）。営業の仕事。
                "awaitingNotification",
                queries.query(new CountAwaitingNotificationQuery(), Integer.class)));
    }
}
