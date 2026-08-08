package com.example.cargotracker.booking.application.internal.queryservices;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 貨物予約の画面表示用データ（CQRS のクエリ側）。
 *
 * <p>荷主名は {@code shipper} テーブルを JOIN して 1 回の SQL で取る。
 * **予約 1 件ごとに荷主を引き直すと、一覧を開くたびに N+1 のクエリが飛ぶ。**
 * これは BC 間の直接参照ではない。読み取り側の SQL であり、Booking の
 * ドメインモデルは Shipper のモデルを知らないままである。
 *
 * @param bookingId     予約 ID（文字列）
 * @param shipperCode   荷主コード
 * @param shipperName   荷主名
 * @param cargoType     貨物種別（列挙子名）
 * @param cargoTypeLabel 貨物種別の表示名
 * @param weight        重量（kg）
 * @param origin        出発地 UN/LOCODE
 * @param destination   目的地 UN/LOCODE
 * @param arrivalDeadline 希望到着期限
 * @param bookingStatus 予約状態（列挙子名）
 * @param statusLabel   予約状態の表示名
 * @param statusBadgeClass 予約状態のバッジ用 Bootstrap クラス（ui_design.md が正典）
 * @param dimensions    寸法（表示用に連結済み。未入力なら空文字）
 * @param quantity      個数（未入力なら {@code null}）
 * @param description   品名（未入力なら空文字）
 * @param daysUntilDeadline 希望期限までの残り日数（過ぎていれば負）
 * @param deadlineUrgencyClass 残り日数に応じた文字色のクラス（ui_design.md が正典）
 * @param assignable    経路設計者に引き渡せるか
 * @param cancellable   キャンセルできるか
 * @param confirmable   予約を確定できるか（US13。経路の割り当てを含めて判断する）
 * @param trackingNumberIssuable 追跡番号を発行できるか（US14）
 * @param trackingNumber 追跡番号。発行前は空文字
 */
public record BookingView(
        String bookingId,
        String shipperCode,
        String shipperName,
        String cargoType,
        String cargoTypeLabel,
        BigDecimal weight,
        String origin,
        String destination,
        LocalDate arrivalDeadline,
        String bookingStatus,
        String statusLabel,
        String statusBadgeClass,
        long daysUntilDeadline,
        String deadlineUrgencyClass,
        String dimensions,
        Integer quantity,
        String description,
        boolean assignable,
        boolean cancellable,
        boolean confirmable,
        boolean trackingNumberIssuable,
        String trackingNumber,
        String routingStatusLabel,
        String routingStatusBadgeClass,
        List<ItineraryLegView> itinerary) {

    public BookingView {
        itinerary = itinerary == null ? List.of() : List.copyOf(itinerary);
    }

    /** 追跡番号が発行済みか。 */
    public boolean hasTrackingNumber() {
        return trackingNumber != null && !trackingNumber.isBlank();
    }

    /** 経路が割り当てられているか。**割り当て済なら旅程がある。** */
    public boolean isRouted() {
        return !itinerary.isEmpty();
    }

    /**
     * 確定した旅程の区間 1 本（US11）。
     *
     * @param voyageNumber   航海番号
     * @param loadLocation   積込港
     * @param unloadLocation 荷降港
     * @param loadTime       積込予定日時
     * @param unloadTime     荷降予定日時
     */
    public record ItineraryLegView(
            String voyageNumber,
            String loadLocation,
            String unloadLocation,
            Instant loadTime,
            Instant unloadTime) {
    }
}
