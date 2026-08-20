package com.example.bookingms.application.port;

import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;

/** 地点マスタ（ADR-010）。正は bookingms が持つ。 */
public interface LocationRepository {

    List<Location> findAll();

    Optional<Location> findByUnLocode(String unLocode);

    /**
     * 地点の業務タイムゾーン。
     *
     * <p>到着期限は目的地の暦で判断する。UTC で判断すると、時差の分だけ受付が拒否される
     * 時間帯ができる。
     */
    Optional<java.time.ZoneId> timeZoneOf(String unLocode);
}
