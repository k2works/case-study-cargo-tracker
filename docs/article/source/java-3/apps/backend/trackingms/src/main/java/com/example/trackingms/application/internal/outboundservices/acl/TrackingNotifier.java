package com.example.trackingms.application.internal.outboundservices.acl;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;

/**
 * 荷主への通知（US17-4・US19-3・US20-4・[ADR-024] 決定 9）。
 *
 * <p><strong>メールは送らない。</strong>通知したという事実を記録し、荷主の画面に出す形で
 * 代替する。<strong>代替であることを画面・マニュアル・完了報告書に明記する</strong>
 * ——書かないと、荷主は「メールが来ないのは不具合」と受け取る。
 *
 * <p>ポートとして置くのは、IT8 で入れるのが代替だからである。メール送信を実装する日に
 * 差し替えるのはこの実装 1 つで済み、業務のコードは動かない。
 */
public interface TrackingNotifier {

    /** 状態が変わった（US17-4）。 */
    void statusChanged(TrackingActivity activity);

    /** 例外が起きた（US19-3・US20-4）。 */
    void exceptionRaised(TrackingActivity activity);

    /** 例外が解決した（US19-4）。 */
    void exceptionResolved(TrackingActivity activity);
}
