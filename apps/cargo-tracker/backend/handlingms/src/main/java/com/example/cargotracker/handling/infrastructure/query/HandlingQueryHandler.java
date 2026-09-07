package com.example.cargotracker.handling.infrastructure.query;

import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handling.infrastructure.persistence.HandlingActivityMapper;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoOnVoyageListView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoOnVoyageView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoSnapshotView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCargoSnapshotQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCargosOnVoyageQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindHandlingHistoryQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.HandlingHistoryItemView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.HandlingHistoryView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.LegView;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 荷役の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class HandlingQueryHandler {

    /** S50 が一度に出す上限。1 隻から 20〜50 本が普通で、それを超えると画面が読めない。 */
    private static final int VOYAGE_ACTIVITY_LIMIT = 200;

    private final CargoSnapshotMapper cargos;
    private final HandlingActivityMapper activities;

    public HandlingQueryHandler(CargoSnapshotMapper cargos, HandlingActivityMapper activities) {
        this.cargos = cargos;
        this.activities = activities;
    }

    /**
     * この航海がこの港で降ろす貨物（S50 の起点）。
     *
     * <p><b>記録済みかどうかも返す。</b> 荷役作業員は「残り何本か」を見ながら進める。
     * 画面が突き合わせると、その判定が画面ごとに増える。</p>
     */
    @QueryHandler
    public CargoOnVoyageListView handle(FindCargosOnVoyageQuery query) {
        Set<String> handled = activities
                .findOnVoyage(query.voyageNumber(), query.unLocode(), VOYAGE_ACTIVITY_LIMIT)
                .stream()
                .filter(row -> !row.voided())
                .map(HandlingActivityMapper.HandlingActivityRow::trackingNumber)
                .collect(Collectors.toSet());

        return new CargoOnVoyageListView(
                cargos.findOnVoyage(query.voyageNumber(), query.unLocode()).stream()
                        .map(row -> new CargoOnVoyageView(row.trackingNumber(), row.bookingId(),
                                row.originUnlocode(), row.destinationUnlocode(), row.cargoType(),
                                handled.contains(row.trackingNumber())))
                        .toList());
    }

    /** 荷役履歴（S51）。<b>取り消した記録も出す</b>——消えていると突き合わせられない。 */
    @QueryHandler
    public HandlingHistoryView handle(FindHandlingHistoryQuery query) {
        List<HandlingHistoryItemView> items =
                activities.findHistory(query.trackingNumber()).stream()
                        .map(row -> new HandlingHistoryItemView(row.activityId(),
                                row.handlingType(),
                                HandlingType.valueOf(row.handlingType()).label(),
                                row.unlocode(), row.voyageNumber(), row.offRoute(),
                                row.operator(), row.completedAt(), row.voided(),
                                row.voidReason()))
                        .toList();
        return new HandlingHistoryView(query.trackingNumber(), items);
    }

    /** 貨物 1 件の写し（S50 の「確認」欄）。見つからなければ {@code null}。 */
    @QueryHandler
    public CargoSnapshotView handle(FindCargoSnapshotQuery query) {
        var row = cargos.findByTrackingNumber(query.trackingNumber());
        if (row == null) {
            return null;
        }
        return new CargoSnapshotView(row.trackingNumber(), row.bookingId(),
                row.originUnlocode(), row.destinationUnlocode(), row.cargoType(),
                cargos.findLegs(row.trackingNumber()).stream()
                        .map(leg -> new LegView(leg.voyageNumber(), leg.loadUnlocode(),
                                leg.unloadUnlocode()))
                        .toList());
    }
}
