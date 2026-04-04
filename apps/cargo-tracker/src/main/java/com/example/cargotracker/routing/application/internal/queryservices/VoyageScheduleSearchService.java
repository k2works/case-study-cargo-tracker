package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.application.internal.outboundservices.VoyageQueryPort;
import com.example.cargotracker.routing.domain.model.Voyage;

import java.time.LocalDate;
import java.util.List;

/**
 * 航海スケジュール検索アプリケーションサービス。
 *
 * <p>出発地・目的地・配達期限を条件として {@link VoyageRepository} を検索し、
 * 期限内に到着可能な航海一覧を返す。
 */
public class VoyageScheduleSearchService {

    private final VoyageQueryPort voyageQueryPort;

    public VoyageScheduleSearchService(VoyageQueryPort voyageQueryPort) {
        this.voyageQueryPort = voyageQueryPort;
    }

    /**
     * 出発地・目的地・配達期限で航海スケジュールを検索する。
     *
     * @param originLocode      出発地 UN/LOCODE
     * @param destinationLocode 目的地 UN/LOCODE
     * @param deadline          配達期限（null の場合は期限フィルタなし）
     * @return 条件に合致する航海リスト
     */
    public List<Voyage> search(String originLocode, String destinationLocode, LocalDate deadline) {
        List<Voyage> candidates = voyageQueryPort.searchVoyages(originLocode, destinationLocode);

        if (deadline == null) {
            return candidates;
        }

        return candidates.stream()
            .filter(voyage -> arrivesBy(voyage, deadline))
            .toList();
    }

    /** 最終 leg の到着日が期限以前かを判定する。 */
    private boolean arrivesBy(Voyage voyage, LocalDate deadline) {
        return voyage.legs().stream()
            .mapToLong(leg -> leg.arrivalDate().toEpochDay())
            .max()
            .stream()
            .allMatch(maxArrival -> maxArrival <= deadline.toEpochDay());
    }
}
