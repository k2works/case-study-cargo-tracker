package com.example.handlingms.application.port;

/**
 * 荷役に起きたことを他のサービスへ伝える出力ポート（[ADR-023] 決定 5）。
 *
 * <p><strong>{@code Port} 接尾辞は付けない。</strong>「何を頼むか」で名付ける。
 *
 * <p>実装は {@code infrastructure/messaging} にあり、そこだけがメッセージ基盤を知る
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 */
public interface HandlingEventNotifier {

    /**
     * 荷役作業を記録したことを伝える。
     *
     * <p><strong>伝わらなかったことを黙って飲み込まない。</strong>失敗が誰にも見えないと、
     * 「荷役は記録したのに追跡が進まない」状態になる。実装はデッドレターへ送る
     * （[ADR-022] 決定 4）。
     */
    void handlingActivityRegistered(HandlingActivityRegistered event);
}
