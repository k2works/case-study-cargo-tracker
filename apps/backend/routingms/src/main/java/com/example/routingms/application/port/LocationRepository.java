package com.example.routingms.application.port;

import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;

/** 地点マスタの出力ポート。bookingms の複製を読む（ADR-014）。 */
public interface LocationRepository {

    List<Location> findAll();

    Optional<Location> findByUnLocode(String unLocode);

    /**
     * 地点の業務タイムゾーン（[ADR-010]）。
     *
     * <p>到着期限は目的地の暦で判断する。bookingms も同じ規則で判定するため、ここを
     * 単一の業務タイムゾーンで代用すると、目的地が東西にずれた分だけ判定が食い違う。
     */
    Optional<java.time.ZoneId> timeZoneOf(String unLocode);
}
