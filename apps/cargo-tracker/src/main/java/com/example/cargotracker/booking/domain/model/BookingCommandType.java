package com.example.cargotracker.booking.domain.model;

/**
 * 予約に対するコマンドの種別。
 *
 * <p>{@code domain-model.md}「コマンド一覧」に対応する。状態遷移の可否は
 * コマンドの種別だけで決まるため、遷移規則は本列挙型を引数に取る
 * （{@link BookingStatus#transitionBy}）。
 *
 * <p>コマンドオブジェクト（{@code BookCargoCommand} 等）はイテレーションごとに
 * 追加されるが、遷移表は最初から全 10 遷移が確定している。**遷移規則の実装を
 * コマンドオブジェクトの実装まで待つと、その間だけ表に無い遷移が通ってしまう。**
 */
public enum BookingCommandType {

    /** 経路設計者に引き渡す（US06）。 */
    ASSIGN_TO_ROUTING,

    /** 旅程を割り当てる（US09 / US11）。BookingStatus は変わらない。 */
    ROUTE_CARGO,

    /** 予約を確定する（US13）。 */
    CONFIRM_BOOKING,

    /** 追跡番号を発行する（US14）。 */
    ASSIGN_TRACKING_NUMBER,

    /** 輸送を開始する（US15。最初の LOAD 荷役による自動遷移）。 */
    START_TRANSPORT,

    /** 引き渡しを完了する（US16。CLAIM 荷役による自動遷移）。 */
    COMPLETE_DELIVERY,

    /** 精算を完了する（US23）。 */
    SETTLE_BOOKING,

    /** 予約をキャンセルする（US04 / US30）。 */
    CANCEL_BOOKING
}
