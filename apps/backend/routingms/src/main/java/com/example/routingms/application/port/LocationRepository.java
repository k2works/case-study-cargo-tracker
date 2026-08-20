package com.example.routingms.application.port;

import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;

/** 地点マスタの出力ポート。bookingms の複製を読む（ADR-014）。 */
public interface LocationRepository {

    List<Location> findAll();

    Optional<Location> findByUnLocode(String unLocode);
}
