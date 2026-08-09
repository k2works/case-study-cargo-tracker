package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import java.util.Optional;

/**
 * 通関の状態を引く（US29 / IT12 の C30）。
 *
 * <p><strong>引き取りに来る当人が確認できないと、通関を記録した意味が半分になる。</strong>
 * IT11 は通関を荷役担当者の画面にだけ出した。荷受人は港へ着いてから
 * 「まだ通っていない」と知ることになる（IT11 レビュー C30）。
 *
 * <p><strong>SQL で JOIN しない。</strong> 追跡照会は Tracking の表から組み立てる。
 * 通関は Handling の持ち物であり、越境してよいのは ACL ポートだけである
 * （ADR-012。{@code MapperTableOwnershipTest} が SQL の越境を検出する）。
 *
 * <p>ポートを定義するのは利用側（Tracking）、実装するのは提供側（Handling）である
 * （{@code CargoContacts} と同じ形）。
 */
public interface CustomsStatuses {

    /**
     * 追跡番号から通関の状態を引く。
     *
     * <p><strong>通関が要らない貨物では空を返す。</strong> 画面はそのとき行そのものを
     * 出さない。国内輸送の荷受人に無関係な「手続き前」を出し続けないためである。
     *
     * @return 通関が要らないか、貨物が見つからなければ空
     */
    Optional<CustomsStatusSummary> findByTrackingNumber(String trackingNumber);

    /**
     * 通関の状態（表示用）。
     *
     * <p><strong>申告番号を運ばない。</strong> 税関に対する書類番号であり、
     * 追跡番号を知る全員に見せる理由がない。公開画面と同じ型を使う以上、
     * <strong>型が持たないことが唯一の保証である</strong>。
     *
     * @param statusLabel 状態の日本語ラベル。申告がまだ無ければ「手続き前」
     * @param allowsClaim 引き取れるか。<strong>状態名だけでは意味が伝わらない</strong>ため、
     *                    画面はこの真偽で「引き取れます／引き取れません」を出す
     */
    record CustomsStatusSummary(String statusLabel, boolean allowsClaim) {
    }
}
