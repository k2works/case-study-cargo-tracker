package com.example.routingms.application.port;

import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageNumber;
import java.util.List;
import java.util.Optional;

/**
 * 航海の出力ポート。実装はインフラ層に置き、依存の向きを内向きに保つ。
 */
public interface VoyageRepository {

    Voyage save(Voyage voyage);

    Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber);

    /** 条件に合う航海を探す。件数の上限は呼び出し側（ユースケース）が決める。 */
    List<Voyage> search(VoyageSearchCriteria criteria, int limit);

    /** 条件に合う航海の総数。上限で切った件数と区別して伝えるために要る。 */
    int countMatching(VoyageSearchCriteria criteria);
}
