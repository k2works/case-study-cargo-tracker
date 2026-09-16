package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .FindShipperBookingProgressQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .FindShipperBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .ShipperBookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries
        .ShipperBookingProgressView;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 荷主向けの予約（S45 自社予約一覧 / S46 自社予約の進み具合）。
 *
 * <p><b>社内向けの {@link BookingController} と分ける。</b> 同じ経路に
 * 荷主を通すと、荷主に見せない項目（金額の材料・社内メモ・担当者名・他社の予約）を
 * 落とす責任が 1 本ずつの分岐に散る。読み口も応答も別に立てて、
 * <b>荷主に出すものだけを運ぶ</b>。</p>
 *
 * <p><b>荷主 ID はヘッダから受ける。</b> 認証の紐付けは authms が持ち、Gateway が
 * 載せる（{@code TrackingController} と同じ形）。要求の本文やクエリから受けると、
 * 他社の荷主 ID を名乗れてしまう。</p>
 *
 * <p><b>紐付けが無ければ断る。</b> 「荷主 ID が無いなら全件」に倒すと、
 * ヘッダを落とすだけで他社の予約が見える。</p>
 */
@RestController
@RequestMapping("/api/v1/booking/shipper/bookings")
public class ShipperBookingController {

    /** 一覧が一度に返す上限。<b>上限を超える指定は切り詰める</b>。 */
    private static final int MAX_LIMIT = 200;

    private final QueryDispatcher queries;

    public ShipperBookingController(QueryDispatcher queries) {
        this.queries = queries;
    }

    /** S45: 自社予約一覧。既定では精算済・キャンセルを外す。 */
    @GetMapping
    public ResponseEntity<ShipperBookingListView> list(
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId,
            @RequestParam(defaultValue = "false") boolean includeFinished,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(queries.query(
                new FindShipperBookingsQuery(required(shipperId), includeFinished,
                        Math.clamp(limit, 1, MAX_LIMIT)),
                ShipperBookingListView.class));
    }

    /**
     * S46: 自社予約の進み具合。
     *
     * <p><b>他社のものと存在しないものを区別しない。</b> どちらも 404 にする——
     * 区別すると、予約 ID を総当たりして「その予約が在ること」を確かめられる。</p>
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<ShipperBookingProgressView> progress(
            @RequestHeader(value = "X-Auth-Shipper-Id", required = false) String shipperId,
            @PathVariable String bookingId) {
        ShipperBookingProgressView view = queries.query(
                new FindShipperBookingProgressQuery(required(shipperId), bookingId),
                ShipperBookingProgressView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    private static String required(String shipperId) {
        if (shipperId == null || shipperId.isBlank()) {
            // 紐付けが済んでいない荷主。全件を見せるより断るほうが害が小さい。
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "荷主の紐付けがありません。担当者にお問い合わせください");
        }
        return shipperId;
    }
}
