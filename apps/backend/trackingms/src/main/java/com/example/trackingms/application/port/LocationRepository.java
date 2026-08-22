package com.example.trackingms.application.port;

import com.example.shared.domain.model.Location;
import java.util.Optional;

/**
 * 地点マスタ（出力ポート）。
 *
 * <p>複製であり、正は bookingms が持つ（[ADR-014]）。<strong>名称をイベントから受け取らない</strong>
 * ——受け取ると、地点名の直しがマスタと追跡の 2 か所に分かれる。
 */
public interface LocationRepository {

    Optional<Location> findByUnLocode(String unLocode);
}
