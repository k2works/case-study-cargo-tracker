package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 予約から「誰に連絡するのか」を引く（US19 / US20）。
 *
 * <p><strong>例外の一覧は連絡すべき仕事の待ち行列である。</strong> 荷主の名前が
 * 読めないと、追跡管理者は 1 件ずつ予約を開いて確かめることになる
 * （IT9 のふりかえり T2「気づく手段を作ったら、そこから次の行動へ行けるかを確かめる」）。
 *
 * <p><strong>ポートを定義するのは利用側（Tracking）、実装するのは提供側（Booking）</strong>
 * である（ADR-005 / ADR-012）。Tracking は Booking のクラスも表も知らない。
 * {@code PortNames} と同じ形である。
 */
public interface CargoContacts {

    /**
     * 予約 ID から荷主名を引く。
     *
     * <p><strong>まとめて引く。</strong> 1 件ずつ引くと、一覧の行数だけ
     * 問い合わせが増える（N+1）。
     *
     * @return 予約 ID をキーとする荷主名。**見つからない予約は含めない**
     */
    Map<UUID, String> findShipperNames(List<UUID> bookingIds);
}
