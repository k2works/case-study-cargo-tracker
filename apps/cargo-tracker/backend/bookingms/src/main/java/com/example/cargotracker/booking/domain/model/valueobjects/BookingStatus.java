package com.example.cargotracker.booking.domain.model.valueobjects;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 予約の状態（domain-model.md「BookingStatus 状態遷移（正典）」）。
 *
 * <p><b>遷移の判定はここ 1 か所に置く。</b> 画面のボタン出し分けは投影の
 * {@code booking_status} を読むが、判定を書き直さずこの述語を呼ぶ。書き直すと、
 * 片方だけ直したときに画面と集約の判断が食い違う。</p>
 *
 * <p>遷移表は IT2 の時点で到達しない先まで正典どおり全部書く。あとから値を足す
 * たびに全箇所を回るのを避けるため。</p>
 */
public enum BookingStatus {
    /** 仮受付。BookCargoCommand で始まる。 */
    PRELIMINARY("仮受付"),
    /** 経路提案中。 */
    ROUTE_PROPOSED("経路提案中"),
    /** 荷主へ経路を通知済み。 */
    ROUTE_NOTIFIED("通知済み"),
    /** 予約確定。 */
    CONFIRMED("確定"),
    /** 追跡番号発行済み。 */
    TRACKING_ISSUED("追跡番号発行済み"),
    /** 輸送中。 */
    IN_TRANSIT("輸送中"),
    /** 引取済。 */
    DELIVERED("引取済"),
    /** 精算済。 */
    SETTLED("精算済"),
    /** キャンセル。 */
    CANCELLED("キャンセル");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    /** 利用者に見せる呼び名。列挙名は見せない。 */
    public String label() {
        return label;
    }

    private static final Map<BookingStatus, Set<BookingStatus>> NEXT = Map.of(
            PRELIMINARY, EnumSet.of(ROUTE_PROPOSED, CANCELLED),
            ROUTE_PROPOSED, EnumSet.of(ROUTE_PROPOSED, ROUTE_NOTIFIED, CANCELLED),
            ROUTE_NOTIFIED, EnumSet.of(ROUTE_NOTIFIED, ROUTE_PROPOSED, CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(TRACKING_ISSUED, CANCELLED),
            TRACKING_ISSUED, EnumSet.of(IN_TRANSIT, CANCELLED),
            IN_TRANSIT, EnumSet.of(IN_TRANSIT, DELIVERED, CANCELLED),
            DELIVERED, EnumSet.of(SETTLED),
            SETTLED, EnumSet.noneOf(BookingStatus.class),
            CANCELLED, EnumSet.noneOf(BookingStatus.class));

    public boolean canTransitionTo(BookingStatus next) {
        return NEXT.get(this).contains(next);
    }

    /**
     * 入力の誤りを直せるか（US32。不変条件「修正できるのは仮受付の予約だけ」）。
     *
     * <p>経路提案中より先へ進むと、経路設計者が既にその内容で作業している。直せると、
     * 設計の前提が黙って変わる。<b>遷移ではないので canTransitionTo では表せない。</b>
     * 画面のボタン出し分けもこの述語を呼ぶ（判定を書き直さない）。</p>
     */
    public boolean canUpdateSpecification() {
        return this == PRELIMINARY;
    }

    /**
     * 申請を挟まずその場でキャンセルできるか。
     *
     * <p>{@code IN_TRANSIT} だけは荷物が動いているので、申請 → 承認（陸揚げ地の指定）の
     * 2 段階になる（不変条件 9）。</p>
     */
    public boolean cancellableImmediately() {
        return canTransitionTo(CANCELLED) && this != IN_TRANSIT;
    }
}
