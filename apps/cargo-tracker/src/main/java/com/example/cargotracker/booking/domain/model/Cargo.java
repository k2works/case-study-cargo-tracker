package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.shared.domain.model.ShipperId;

/**
 * 貨物。Booking Context の集約ルート。
 *
 * <p>状態遷移の規則は {@link BookingStatus} が持ち、本クラスは
 * 「どのコマンドをいつ実行してよいか」を集約の文脈で判断する。
 *
 * <p><strong>Setter を持たない。</strong> 状態を変える手段は業務のことばで名づけた
 * 振る舞い（{@link #cancel()} 等）に限る。Setter を生やすと、不変条件を通らずに
 * 状態を書き換える経路ができる。
 */
public class Cargo {

    private final BookingId bookingId;
    private final ShipperId shipperId;
    private final CargoSpecification cargoSpecification;
    private final RouteSpecification routeSpecification;
    private final long version;

    private BookingStatus bookingStatus;

    private Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification,
            BookingStatus bookingStatus,
            long version) {
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.cargoSpecification = cargoSpecification;
        this.routeSpecification = routeSpecification;
        this.bookingStatus = bookingStatus;
        this.version = version;
    }

    /**
     * 予約を登録する（遷移表 #1）。
     *
     * <p>ビジネスルール 1: 貨物は必ず BookingId・ShipperId・CargoType を持つ。
     */
    public static Cargo book(BookCargoCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("予約コマンドは必須です");
        }
        if (command.shipperId() == null) {
            throw new IllegalArgumentException("荷主は必須です");
        }
        if (command.cargoSpecification() == null) {
            throw new IllegalArgumentException("貨物仕様は必須です");
        }
        if (command.routeSpecification() == null) {
            throw new IllegalArgumentException("出発地・目的地・到着期限は必須です");
        }
        return new Cargo(
                BookingId.generate(),
                command.shipperId(),
                command.cargoSpecification(),
                command.routeSpecification(),
                BookingStatus.initial(),
                0L);
    }

    /**
     * 永続化された状態から復元する。
     *
     * <p><strong>状態は保存された値をそのまま使い、履歴から導出しない。</strong>
     * 導出すると、ユニットテストが緑のままでも別リクエストで状態が巻き戻る。
     */
    public static Cargo reconstruct(
            BookingId bookingId,
            ShipperId shipperId,
            CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification,
            BookingStatus bookingStatus,
            long version) {
        return new Cargo(
                bookingId, shipperId, cargoSpecification, routeSpecification, bookingStatus, version);
    }

    /**
     * 経路設計者に引き渡せるか（遷移表 #2。US06）。
     *
     * <p>画面のボタン出し分けは本述語をそのまま呼ぶ。**引き渡し済みの予約に
     * 「引き渡す」ボタンが出ていると、二重に依頼が飛ぶ。**
     */
    public boolean canAssignToRouting() {
        return bookingStatus.canTransitionBy(BookingCommandType.ASSIGN_TO_ROUTING);
    }

    /**
     * 経路設計者に引き渡す（US06）。
     *
     * @throws InvalidBookingStatusTransitionException 引き渡せない状態のとき
     */
    public void assignToRouting() {
        this.bookingStatus = bookingStatus.transitionBy(BookingCommandType.ASSIGN_TO_ROUTING);
    }

    /**
     * キャンセルできるか（遷移表 #9 / #10）。
     *
     * <p>画面のボタン出し分けは本述語をそのまま呼ぶ。
     */
    public boolean canCancel() {
        return bookingStatus.canTransitionBy(BookingCommandType.CANCEL_BOOKING);
    }

    /**
     * 予約をキャンセルする。
     *
     * @throws InvalidBookingStatusTransitionException キャンセルできない状態のとき
     */
    public void cancel() {
        this.bookingStatus = bookingStatus.transitionBy(BookingCommandType.CANCEL_BOOKING);
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public ShipperId shipperId() {
        return shipperId;
    }

    public CargoSpecification cargoSpecification() {
        return cargoSpecification;
    }

    public RouteSpecification routeSpecification() {
        return routeSpecification;
    }

    public BookingStatus bookingStatus() {
        return bookingStatus;
    }

    public long version() {
        return version;
    }
}
