package com.example.trackingms.domain.model;

import java.util.Optional;

/**
 * 追跡の状況（Tracking Context）。
 *
 * <p><strong>bookingms の {@code TrackingStatus} とは別のものである。</strong>IT6 の実装は
 * こちらも {@code TrackingStatus} と名付けていたが、その名前は設計では
 * <strong>Booking Context の {@code Delivery} が持つもの</strong>であり、BC をまたいで
 * 同じ名前が別物を指していた（IT7 計画の注 4）。設計の名前に合わせて改名した。
 *
 * <p>値は[ドメインモデル](../../../../../../../docs/design/domain-model.md)の
 * {@code TrackingStatus} に合わせる。IT7 で実際に通るのは荷役で動く 5 つ
 * （{@link #RECEIVED} / {@link #LOADED} / {@link #UNLOADED} / {@link #AWAITING_CLAIM} /
 * {@link #CLAIMED}）である。
 */
public enum TrackingStatus {

    /**
     * まだ受け取っていない。
     *
     * <p><strong>空欄ではなく意味のある状態である</strong>（[ADR-009]）。列を nullable に
     * して後から必須にすると、それまでに入った行が読めなくなる。
     */
    NOT_RECEIVED,

    /** 出発港で預かった。積込を待つ。 */
    RECEIVED,

    /** 船に積んだ。出港を待つ。 */
    LOADED,

    /**
     * 船の上。
     *
     * <p><strong>荷役の記録では起きない。</strong>出港の反映は US17（IT8）である。
     * 遷移が存在することだけをここに残す。
     */
    ONBOARD_CARRIER,

    /** 船から降ろした。積み替えか、目的港での引取待ちへ進む。 */
    UNLOADED,

    /** 目的港で荷受人の引取を待っている。 */
    AWAITING_CLAIM,

    /** 荷受人へ引き渡した。配送完了。 */
    CLAIMED,

    /**
     * 例外が起きている。
     *
     * <p><strong>IT7 では使わない。</strong>例外の起票は US20（IT8）である。
     */
    EXCEPTION,

    /**
     * 状態が読めない。
     *
     * <p><strong>新規には選べない。</strong>状態が読めない行のためのものであり、
     * 業務の操作でここへ来ることはない。
     */
    UNKNOWN;

    /**
     * 荷役の種別から、進む先を導く（[ADR-023] 決定 5・US15-4）。
     *
     * <p><strong>目的港での荷降しだけは行き先が違う。</strong>途中の港なら次の積込を待ち
     * （{@link #UNLOADED}）、目的港なら荷受人の引取を待つ（{@link #AWAITING_CLAIM}）。
     * 同じ「荷降し」でも、貨物にとっての意味が違う。
     *
     * <p>導けない種別は空を返す。ここで例外にすると、購読側が種別 1 つで止まる。
     *
     * @param handlingType 荷役の種別（`HandlingType` の名前。相手の型は持ち込まない）
     * @param atDestination 作業場所が目的港か
     */
    public static Optional<TrackingStatus> afterHandling(String handlingType,
            boolean atDestination) {
        return switch (handlingType) {
            case "RECEIVE" -> Optional.of(RECEIVED);
            case "LOAD" -> Optional.of(LOADED);
            case "UNLOAD" -> Optional.of(atDestination ? AWAITING_CLAIM : UNLOADED);
            case "CLAIM" -> Optional.of(CLAIMED);
            default -> Optional.empty();
        };
    }
}
