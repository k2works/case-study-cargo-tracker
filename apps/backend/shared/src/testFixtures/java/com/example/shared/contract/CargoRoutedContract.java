package com.example.shared.contract;

import java.util.List;

/**
 * 「経路が決まった」ことのイベント契約（[ADR-024] 決定 4）。
 *
 * <p><strong>3 本目の契約である。</strong>bookingms が旅程から到着の見込みを出し、
 * trackingms がそれを持つ。trackingms は旅程を持たないため、これが無いと
 * US18-2 の「推定到着日」を出せない。
 *
 * <p><strong>ACL は引かない。</strong>公開照会は認証の外にあり、1 件の照会が bookingms への
 * 同期呼び出しになると、総当たりがそのまま bookingms への負荷に化ける（決定 4）。
 *
 * <p><strong>旅程そのものは運ばない。</strong>trackingms が要るのは日付 1 つで、旅程を写すと
 * [ADR-019] の ACL と二重の写しになる。
 */
public final class CargoRoutedContract {

    private CargoRoutedContract() {
    }

    /** 交換機。予約のイベントと同じ交換機を使う——送り手も受け手も同じ 2 者である。 */
    public static final String EXCHANGE = "cargoBookingChannel";

    /** ルーティングキー。 */
    public static final String ROUTING_KEY = "cargo.cargo-routed";

    /**
     * 流れる項目。<strong>順序も含めて契約である</strong>。
     *
     * <p>追跡番号を載せるのは、受け手が<strong>それで自分の集約を引く</strong>ためである。
     * 予約番号でも引けるが、追跡の業務キーは追跡番号であり、そちらで揃える。
     */
    public static final List<String> FIELDS = List.of(
            "trackingNumber", "bookingId", "estimatedArrival", "occurredAt");

    /**
     * プロデューサが {@code __TypeId__} に載せる型名。
     *
     * <p>この名前は<strong>コンシューマのクラスパスに存在しない</strong>。
     */
    public static final String PRODUCER_TYPE_ID =
            "com.example.bookingms.application.port.CargoRouted";
}
