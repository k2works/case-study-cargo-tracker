package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 予約から「誰に連絡するのか」「何を運んでいるのか」を引く（US19 / US20 / C19）。
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

    /**
     * 予約 ID から貨物の要約を引く（IT11 / C19）。
     *
     * <p><strong>エスカレーションで管理者が決めるのは「保険を使うか」
     * 「補償に進むか」である。</strong> そのためには、どこからどこへ運ぶ
     * 何の貨物なのかが要る。追跡番号と荷主名だけでは判断できない。
     *
     * <p>境界の外に出すのは<strong>表示のための値だけ</strong>で、
     * 予約のドメインオブジェクトは渡さない。
     *
     * @return 見つからなければ空
     */
    java.util.Optional<CargoSummary> findSummary(UUID bookingId);

    /**
     * 貨物の要約（表示用）。
     *
     * @param origin      出発地の UN/LOCODE
     * @param destination 目的地の UN/LOCODE
     * @param cargoType   貨物種別の表示名
     * @param weight      重量の表示文字列（単位を含む）
     */
    record CargoSummary(String origin, String destination, String cargoType, String weight) {
    }
}
