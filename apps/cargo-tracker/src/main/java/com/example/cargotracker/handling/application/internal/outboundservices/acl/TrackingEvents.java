package com.example.cargotracker.handling.application.internal.outboundservices.acl;

import java.time.Instant;

/**
 * 荷役の結果を追跡に伝える出力ポート（Handling → Tracking の ACL）。
 *
 * <p><strong>境界では素の値だけを渡す。</strong> 荷役は {@code TrackingActivity} も
 * {@code TransportStatus} も知らない。伝えるのは「いつ・どこで・何をしたか」であり、
 * それが輸送状態のどれに当たるかは Tracking が決める（ADR-005）。
 *
 * <p>Handling を独立した BC に昇格したことで（ADR-010）、この経路にも ACL が要る。
 * <strong>同期・同一トランザクションで呼ぶ</strong>（ADR-009）。荷役だけが記録されて
 * 追跡に現れない中間状態を作らないためである。
 */
public interface TrackingEvents {

    /**
     * 荷役の結果を追跡イベントとして記録する。
     *
     * @param trackingNumber 追跡番号
     * @param eventType      荷役種別の名前（{@code RECEIVE} / {@code LOAD} など）
     * @param occurredAt     発生日時
     * @param locationUnlocode 発生場所（UN/LOCODE）
     * @param voyageNumber   航海番号。無い場合は {@code null}
     * @return 記録の結果
     */
    Result record(
            String trackingNumber,
            String eventType,
            Instant occurredAt,
            String locationUnlocode,
            String voyageNumber);

    /** 記録の結果。 */
    enum Result {
        /** 記録した。 */
        RECORDED,
        /**
         * 追跡レコードが無い。
         *
         * <p><strong>荷役の登録は続ける。</strong> 追跡が無いことを理由に、
         * 実際に起きた作業の記録まで失うほうが損失が大きい。
         */
        NOT_FOUND,
        /** 他の操作が先行していた（楽観的ロック）。 */
        CONFLICTED
    }
}
