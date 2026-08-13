package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 追跡番号の発行を依頼する出力ポート（Booking → Tracking の ACL）。
 *
 * <p>正典は {@code domain-model.md}「BC 間 ACL ポート一覧」である。
 *
 * <p><strong>採番も追跡レコードの作成も Tracking の仕事である。</strong> Booking が
 * 受け取るのは発行された番号の文字列だけであり、{@code TrackingActivity} や
 * {@code TransportStatus} を知らない（ADR-005・ArchUnit ルール 4）。
 *
 * <p>実装は Tracking 側の {@code infrastructure/acl} が持つ。
 */
public interface TrackingPort {

    /**
     * 予約に対する追跡を開始し、発行した追跡番号を返す。
     *
     * <p><strong>目的地と推定到着日を一緒に渡す</strong>（ADR-012）。渡さずに
     * Tracking から Booking へ問い合わせると、Booking → Tracking（本ポート）と
     * 合わせてパッケージが循環する。<strong>逆向きのポートを足す前に、
     * 順方向の呼び出しでデータを渡せないかを先に問う。</strong>
     *
     * <p>運ぶのは素の値だけである（ADR-005）。
     *
     * @param bookingId            予約 ID
     * @param destinationUnlocode  目的地（UN/LOCODE）
     * @param estimatedArrivalDate 推定到着日。経路が未確定なら {@code null}
     * @return 発行した追跡番号（{@code TRK-YYYYMMDD-NNNN}）
     */
    String issue(UUID bookingId, String destinationUnlocode, LocalDate estimatedArrivalDate);
}
