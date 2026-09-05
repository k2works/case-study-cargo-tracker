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

    /**
     * 状態ごとの件数（S02 の「今日の作業」）。
     *
     * <p>「仮受付の件数」に限定しない。誤配の件数（S30）も同じ形で数えるので、
     * 状態を引数に取る。専用のクエリを状態の数だけ足すと、増やすたびに配線が増える。</p>
     */
    public record CountBookingsByStatusQuery(String bookingStatus) {
    }

    /**
     * 経路設計作業一覧（S30）。
     *
     * <p>{@code includeRouted} は「設計済みも表示」の操作に対応する。既定を false に
     * しているのは、設計の済んだ予約が混ざると一覧全体が「今日やること」として
     * 信用されなくなるため。誤配は既定でも含める（現在地からの再設計が要る）。</p>
     */
    public record FindRoutingWorklistQuery(int page, int size, boolean includeRouted) {
    }

    /**
     * 修正履歴（S22 / US32 §受入基準 4）。
     *
     * <p>一覧（{@code FindBookingsQuery}）には載せない。全件ぶんの履歴を読むことに
     * なるうえ、一覧では読まない。</p>
     */
    public record FindBookingRevisionsQuery(String bookingId) {
    }

    /** 1 回の修正で変わった項目 1 つ。新しい修正が先に並ぶ。 */
    public record RevisionView(
            Instant updatedAt,
            String updatedBy,
            String label,
            String before,
            String after) {
    }

    public record RevisionListView(List<RevisionView> items) {
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
            Instant bookedAt,
            // 経路設計者へ引き渡した日時（US06）。引き渡していなければ null。
            // 期限が遠い案件が S30 の下に沈んで放置されたことに気づく手立て。
            Instant routingRequestedAt,
            // 最終更新（US32）。変更内容の履歴は Event Store が持つ。
            Instant updatedAt,
            String updatedBy) {
    }

    public record BookingListView(List<BookingView> items, int total) {
    }
}
