package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import java.time.Instant;
import java.util.Optional;

/**
 * 貨物の到着見込みを取得する出力ポート（Tracking → Booking の ACL）。
 *
 * <p>US18 の受入基準は<strong>推定到着日</strong>の表示を求めるが、旅程を持つのは
 * Booking の {@code CargoItinerary} である。Tracking は自分では答えを持たない。
 *
 * <p><strong>これは問い合わせであり、状態の伝播ではない</strong>（ADR-009）。
 * 読むだけであり可否をその場で返す必要もないため、同期の ACL ポートで扱う。
 * ドメインイベントにすると、Tracking が Booking の旅程の写しを持ち続けることになる。
 *
 * <p><strong>境界を越える値は本インターフェースの内側に置く。</strong> Booking の
 * {@code domain.model} を参照すると ArchUnit ルール 4 に落ちる
 * （ACL ポートのパッケージだけが越境点として除外されている）。
 *
 * <p>ポート名は複数形、運ぶ値は単数形とする（IT6 で定めた規約）。
 * 実装は Booking 側の {@code infrastructure/acl} が持つ。
 */
public interface CargoArrivalEstimates {

    /**
     * 予約 ID から到着見込みを取得する。
     *
     * @param bookingId 予約 ID
     * @return 予約が見つからなければ空
     */
    Optional<CargoArrivalEstimate> findByBookingId(String bookingId);

    /**
     * 到着見込み。<strong>すべて素の値である。</strong>
     *
     * <p><strong>遅延は織り込まない。</strong> 確定した旅程の最終区間の荷降予定日時を
     * そのまま返す。遅延を反映した再計算は US19（IT10）であり、その入力となる
     * 遅延イベントはまだ存在しない。<strong>存在しない根拠で数字を作らない。</strong>
     *
     * @param destination       目的地（UN/LOCODE）
     * @param estimatedArrival  推定到着日時。経路が未確定なら {@code null}
     */
    record CargoArrivalEstimate(String destination, Instant estimatedArrival) {

        /** 経路が確定しているか。未確定なら画面は「未確定」と表示する。 */
        public boolean isEstimated() {
            return estimatedArrival != null;
        }
    }
}
