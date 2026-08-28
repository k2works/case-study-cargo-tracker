package com.example.bookingms.application.internal.outboundservices.acl;

/**
 * 予約に起きたことを他のサービスへ伝える出力ポート（[ADR-022]）。
 *
 * <p><strong>{@code Port} 接尾辞は付けない。</strong>「何を頼むか」で名付ける（IT5 で確立した規約）。
 *
 * <p>実装は {@code infrastructure/messaging} にあり、そこだけがメッセージ基盤を知る
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 */
public interface CargoEventNotifier {

    /**
     * 追跡番号を発行したことを伝える。
     *
     * <p><strong>伝わらなかったことを黙って飲み込まない。</strong>戻り値を捨てる実装にすると、
     * 失敗が誰にも見えないまま「追跡番号は出したのに追跡が無い」状態になる。実装は
     * デッドレターへ送る（[ADR-022] 決定 4）。
     */
    void trackingNumberIssued(TrackingNumberIssued event);

    /**
     * キャンセルが確定したことを伝える（US30・[ADR-025] 決定 3）。
     *
     * <p><strong>購読者がいるから発行する。</strong>公開追跡が開いているため、キャンセルが
     * 承認された貨物を荷主が引くと trackingms は「輸送中」のまま返す——荷主は自分が
     * 申し入れて承認されたキャンセルを、画面で否定されることになる。
     *
     * <p><strong>billingms へは発行しない。</strong>キャンセル料の算定は US21（IT11）であり、
     * 受け口が無い。読む側の無い配線を先に敷かない——同じイベントに購読者を足すだけで
     * 済む形（トピック交換機 + 購読側ごとのキュー）にしてある。
     */
    void cargoCancelled(CargoCancelled event);

}
