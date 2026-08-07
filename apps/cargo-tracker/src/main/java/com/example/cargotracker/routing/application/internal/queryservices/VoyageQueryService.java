package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 航海スケジュールの読み取り（US07。CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface VoyageQueryService {

    /**
     * 航海スケジュールを検索する。
     *
     * <p>絞り込みは<strong>すべて SQL 側で行う</strong>。読み込んでから
     * Java で filter すると、航海が増えたときに一覧を開くだけで全件が載る。
     *
     * @param origin      出発地 UN/LOCODE。未指定なら絞り込まない
     * @param destination 目的地 UN/LOCODE。未指定なら絞り込まない
     * @param departureFrom 出発日の下限。未指定なら絞り込まない
     * @param departureTo   出発日の上限。未指定なら絞り込まない
     * @param cargoType   運べる貨物種別。未指定なら絞り込まない
     * @param page        ページ送りの要求
     */
    Page<VoyageView> search(
            String origin,
            String destination,
            LocalDate departureFrom,
            LocalDate departureTo,
            RoutingCargoType cargoType,
            PageRequest page);

    /**
     * 航海詳細を 1 件読む（全区間つき）。
     *
     * <p>区間は<strong>出発順</strong>に並べて返す。並びが崩れると、寄港地の
     * 順序が意味を失う。
     *
     * @param voyageNumber 航海番号
     * @return 見つからなければ空
     */
    Optional<VoyageDetailView> findDetail(String voyageNumber);
}
