package com.example.cargotracker.booking.domain.model.valueobjects;

import java.util.Set;

/**
 * 予約がどこまで進んだか（予約状態・経路・追跡番号のひと組）。
 *
 * <p><strong>3 つを別々に持たない。</strong> 「まだ確定していないのに追跡番号がある」
 * のような組み合わせは業務上あり得ず、別々の項目にすると作れてしまう
 * （IT5 の {@link CargoRouting} と同じ理由）。
 *
 * <p><strong>{@code TransportStatus} は持たない。</strong> 輸送状態の所有は
 * Tracking Context であり（ADR-005）、Booking が持つと同じ事実が 2 か所に存在する。
 * {@code domain-model.md} の {@code Delivery} を本 IT で導入しないのはこのためである。
 *
 * @param status         予約状態
 * @param routing        経路（状態と旅程）
 * @param trackingNumber 追跡番号。発行前は {@code null}
 */
public record CargoProgress(
        BookingStatus status,
        CargoRouting routing,
        BookingTrackingNumber trackingNumber) {

    /** 追跡番号を発行する前の状態（遷移表 #5 より前）。 */
    private static final Set<BookingStatus> BEFORE_TRACKING_ISSUED = Set.of(
            BookingStatus.PRELIMINARY, BookingStatus.ROUTE_PROPOSED, BookingStatus.CONFIRMED);

    public CargoProgress {
        if (status == null) {
            throw new IllegalArgumentException("予約状態は必須です");
        }
        if (routing == null) {
            throw new IllegalArgumentException("経路は必須です（未割り当ては notRouted で表す）");
        }
        if (trackingNumber != null && BEFORE_TRACKING_ISSUED.contains(status)) {
            throw new IllegalArgumentException(
                    "追跡番号は確定した予約に発行します: " + status.displayName());
        }
    }

    /**
     * 予約を確定できるか（遷移表 #4 とその事前条件）。
     *
     * <p><strong>判断はここ 1 か所に置く。</strong> 集約（{@code Cargo.canConfirm}）も
     * 画面に渡す読み取りモデルも本メソッドを呼ぶ。同じ規則を 2 か所に書くと、
     * <strong>押せるのに実行すると失敗するボタン</strong>が生まれる。
     *
     * <p>事前条件は状態だけでは足りない。経路が割り当てられていない予約を
     * 確定すると、運ぶ道筋の無い予約に荷主の同意が付く。
     */
    public static boolean confirmable(BookingStatus status, CargoRoutingStatus routingStatus) {
        return routingStatus == CargoRoutingStatus.ROUTED
                && status.canTransitionBy(BookingCommandType.CONFIRM_BOOKING);
    }

    /** 新規予約の進み方（仮予約・経路未割り当て・追跡番号なし）。 */
    public static CargoProgress initial() {
        return new CargoProgress(BookingStatus.initial(), CargoRouting.notRouted(), null);
    }

    /** 予約状態だけを進めた複製。 */
    // **分割で公開せざるを得なくなった**（IT19 の C8。ADR-024）
    public CargoProgress withStatus(BookingStatus next) {
        return new CargoProgress(next, routing, trackingNumber);
    }

    /** 経路だけを差し替えた複製。 */
    // **同上**
    public CargoProgress withRouting(CargoRouting next) {
        return new CargoProgress(status, next, trackingNumber);
    }

    /** 状態を進めて追跡番号を付けた複製（遷移表 #5）。 */
    // **分割で公開せざるを得なくなった**（IT19 の C8。ADR-024）
    public CargoProgress issued(BookingStatus next, BookingTrackingNumber issued) {
        return new CargoProgress(next, routing, issued);
    }
}
