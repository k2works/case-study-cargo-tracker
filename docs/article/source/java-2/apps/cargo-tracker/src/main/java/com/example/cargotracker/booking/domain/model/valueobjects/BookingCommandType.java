package com.example.cargotracker.booking.domain.model.valueobjects;

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

    /**
     * 引き渡しの完了を取り消す（US36。承認された取り消しによる遷移）。
     *
     * <p><strong>手で戻す操作ではない。</strong> 追跡管理者の承認を経た
     * 取り消しだけがこの遷移を起こす。
     */
    REVERT_DELIVERY,

    /** 精算を完了する（US23）。 */
    SETTLE_BOOKING,

    /**
     * 予約をキャンセルする（US04。遷移表 #9）。
     *
     * <p><strong>輸送開始前だけである。</strong> 輸送中は承認を伴う
     * {@link #APPROVE_CANCEL} を使う — 貨物が船の上にあり、
     * <strong>どこで降ろすかを決めないままキャンセルすると貨物が宙に浮く</strong>。
     */
    CANCEL_BOOKING,

    /**
     * 輸送中のキャンセルを承認する（US30。遷移表 #10）。
     *
     * <p><strong>営業担当者の操作では起こらない。</strong> 追跡管理者が
     * 陸揚げ地を決めて承認したときだけこの遷移が起きる
     * （{@link #REVERT_DELIVERY} と同じ形）。
     */
    APPROVE_CANCEL
}
