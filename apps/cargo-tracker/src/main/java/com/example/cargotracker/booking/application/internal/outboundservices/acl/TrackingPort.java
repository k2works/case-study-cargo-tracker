package com.example.cargotracker.booking.application.internal.outboundservices.acl;

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
     * @param bookingId 予約 ID
     * @return 発行した追跡番号（{@code TRK-YYYYMMDD-NNNN}）
     */
    String issue(UUID bookingId);
}
