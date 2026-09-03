package com.example.cargotracker.booking.infrastructure.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 貨物予約の読み取りモデル（domain-model.md「クエリ一覧」）。 */
public final class BookingQueries {

    private BookingQueries() {
    }

    public record FindBookingQuery(String bookingId) {
    }

    /**
     * 一覧（S20）。
     *
     * <p>{@code includeFinished} は「終了したものも表示」の操作に対応する。既定を
     * false にしているのは、精算済とキャンセルが混ざると一覧全体が「今日やること」
     * として信用されなくなるため（ui_design.md「一覧の既定条件」）。</p>
     */
    public record FindBookingsQuery(int page, int size, boolean includeFinished) {
    }

    /** 経路設計の作業量（S02 の「今日の作業」）。 */
    public record CountPreliminaryBookingsQuery() {
    }

    /** 画面に出す予約。荷主名は鍵破棄後に {@code null} になる。 */
    public record BookingView(
            String bookingId,
            String bookingNumber,
            String shipperId,
            String shipperName,
            String originUnLocode,
            String destinationUnLocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            int quantity,
            String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC,
            String bookingStatus,
            String routingStatus,
            Instant bookedAt) {
    }

    public record BookingListView(List<BookingView> items, int total) {
    }
}
