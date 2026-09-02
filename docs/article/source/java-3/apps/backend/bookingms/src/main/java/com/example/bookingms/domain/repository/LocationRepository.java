package com.example.bookingms.domain.repository;

import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;

/** 地点マスタ（ADR-010）。正は bookingms が持つ。 */
public interface LocationRepository {

    List<Location> findAll();

    Optional<Location> findByUnLocode(String unLocode);

    /**
     * UN/LOCODE → 地域区分（[ADR-027] 決定 1 の改訂）。
     *
     * <p>料金の試算に使う。<strong>共有カーネルの {@code Location} には持たせない</strong>
     * ——区分は運賃の話であり、全 BC が共有する概念ではない。
     */
    java.util.Map<String, String> regionsByUnLocode();

    /**
     * 地点の業務タイムゾーン。
     *
     * <p>到着期限は目的地の暦で判断する。UTC で判断すると、時差の分だけ受付が拒否される
     * 時間帯ができる。
     */
    Optional<java.time.ZoneId> timeZoneOf(String unLocode);
}
