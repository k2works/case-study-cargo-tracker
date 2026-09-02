package com.example.handlingms.domain.repository;

import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;

/**
 * 地点マスタ（[ADR-014] の複製）。
 *
 * <p><strong>作業場所は地点マスタから選ぶ</strong>（US15-3）。自由入力にすると、
 * 綴りの揺れた港が記録に入り、照合が働かなくなる。
 */
public interface LocationRepository {

    Optional<Location> findByUnLocode(String unLocode);

    /** 画面の選択肢に使う。 */
    List<Location> findAll();
}
