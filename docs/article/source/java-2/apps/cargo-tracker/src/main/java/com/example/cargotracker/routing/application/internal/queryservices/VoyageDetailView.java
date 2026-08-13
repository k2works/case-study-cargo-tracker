package com.example.cargotracker.routing.application.internal.queryservices;

import java.time.Instant;
import java.util.List;

/**
 * 航海詳細の画面表示用データ（CQRS のクエリ側）。
 *
 * <p>一覧（{@link VoyageView}）は航海の端点しか持たない。<strong>乗り継ぎ便では
 * 寄港地ごとの発着時刻が分からないと経路を組めない</strong>ため、詳細は全区間を持つ。
 *
 * @param voyageNumber    航海番号
 * @param vesselName      船名
 * @param carrierName     運送会社
 * @param cargoTypeLabels 取り扱える貨物種別の表示名
 * @param movements       全区間。<strong>出発順</strong>に並ぶ
 */
public record VoyageDetailView(
        String voyageNumber,
        String vesselName,
        String carrierName,
        List<String> cargoTypeLabels,
        List<Movement> movements) {

    public VoyageDetailView {
        cargoTypeLabels = List.copyOf(cargoTypeLabels);
        movements = List.copyOf(movements);
    }

    /** 出発港。区間の順序が正しいことが前提である。 */
    public String origin() {
        return movements.getFirst().departure();
    }

    /** 目的港。 */
    public String destination() {
        return movements.getLast().arrival();
    }

    /** 直行便か。区間が 1 本なら乗り継ぎが無い。 */
    public boolean isDirect() {
        return movements.size() == 1;
    }

    /**
     * 運送区間 1 本。
     *
     * @param departure     出発港 UN/LOCODE
     * @param departureName 出発港の名称
     * @param arrival       到着港 UN/LOCODE
     * @param arrivalName   到着港の名称
     * @param departureTime 出発時刻
     * @param arrivalTime   到着時刻
     */
    public record Movement(
            String departure,
            String departureName,
            String arrival,
            String arrivalName,
            Instant departureTime,
            Instant arrivalTime) {
    }
}
