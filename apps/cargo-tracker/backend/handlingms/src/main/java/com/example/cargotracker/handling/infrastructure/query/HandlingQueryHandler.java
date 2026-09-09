package com.example.cargotracker.handling.infrastructure.query;

import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.handling.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshots;
import com.example.cargotracker.handling.infrastructure.persistence.HandlingActivityMapper;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoOnVoyageListView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoOnVoyageView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.CargoSnapshotView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCargoSnapshotQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindAwaitingClaimQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindCargosOnVoyageQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindHandlingHistoryQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.HandlingHistoryItemView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.HandlingHistoryView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.FindVoyagePortsQuery;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.LegView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.VoyagePortListView;
import com.example.cargotracker.handling.infrastructure.query.HandlingQueries.VoyagePortView;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 荷役の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class HandlingQueryHandler {

    /**
     * 航海と港の一覧の上限（IT10 レビュー N6）。
     *
     * <p>荷役の現場が 1 日に扱う航海はせいぜい数十で、200 は実務の幅を十分に
     * 超える。<b>運用日数に比例して伸びる読み口を残さない。</b></p>
     */
    private static final int VOYAGE_PORT_LIMIT = 200;

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
        // **種別ごとに数える**（M14）。引取が入ると同じ港で荷降し → 引取が起きるので、
        // 1 つの真偽値では「荷降しは済んだが引取はまだ」を表せない。
        Map<String, List<String>> handled = activities
                .findOnVoyage(query.voyageNumber(), query.unLocode(), VOYAGE_ACTIVITY_LIMIT)
                .stream()
                .filter(row -> !row.voided())
                .collect(Collectors.groupingBy(
                        HandlingActivityMapper.HandlingActivityRow::trackingNumber,
                        Collectors.mapping(
                                HandlingActivityMapper.HandlingActivityRow::handlingType,
                                Collectors.toList())));

        return new CargoOnVoyageListView(
                cargos.findOnVoyage(query.voyageNumber(), query.unLocode()).stream()
                        .map(row -> new CargoOnVoyageView(row.trackingNumber(), row.bookingId(),
                                row.originUnlocode(), row.destinationUnlocode(), row.cargoType(),
                                handled.getOrDefault(row.trackingNumber(), List.of())))
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
                                row.unlocode(), row.voyageNumber(), row.consigneeName(),
                                row.offRoute(),
                                row.operator(), row.completedAt(), row.voided(),
                                row.voidedAt(), row.voidedBy(), row.voidReason()))
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
        var legs = cargos.findLegs(row.trackingNumber());
        return new CargoSnapshotView(row.trackingNumber(), row.bookingId(),
                row.originUnlocode(), row.destinationUnlocode(), row.cargoType(),
                legs.stream()
                        .map(leg -> new LegView(leg.voyageNumber(), leg.loadUnlocode(),
                                leg.unloadUnlocode()))
                        .toList(),
                offRouteByType(row, legs, query.unLocode()));
    }

    /**
     * 種別ごとに、その港での作業が予定外か（H.5）。
     *
     * <p><b>判定は {@link CargoSnapshot#isOffRoute} が答える。</b> 画面に書き直させると、
     * 本番と画面が別の判定を持ち、片方だけが正しい形になる。</p>
     *
     * <p>港を渡さなければ {@code null}。S51（荷役履歴）は港を持たない。</p>
     */
    private Map<String, Boolean> offRouteByType(CargoSnapshotMapper.CargoSnapshotRow row,
            List<CargoSnapshotMapper.CargoSnapshotLegRow> legs, String unLocode) {
        if (unLocode == null || unLocode.isBlank()) {
            return null;
        }
        CargoSnapshot snapshot = CargoSnapshots.of(row, legs);
        Location location = Location.of(unLocode.toUpperCase(java.util.Locale.ROOT));
        Map<String, Boolean> result = new java.util.LinkedHashMap<>();
        for (HandlingType type : HandlingType.values()) {
            result.put(type.name(), snapshot.isOffRoute(type, location));
        }
        return result;
    }

    /**
     * その港で引取を待っている貨物（H.8 / US16）。
     *
     * <p><b>航海起点では辿り着けない。</b> 引取は船から降りたあとの作業で、
     * どの航海の仕事でもない。目的港に居る荷役作業員の 2 つ目の入口になる。</p>
     */
    @QueryHandler
    public CargoOnVoyageListView handle(FindAwaitingClaimQuery query) {
        return new CargoOnVoyageListView(
                cargos.findAwaitingClaim(query.unLocode()).stream()
                        .map(row -> new CargoOnVoyageView(row.trackingNumber(), row.bookingId(),
                                row.originUnlocode(), row.destinationUnlocode(), row.cargoType(),
                                // 引取はまだ。荷降しは済んでいるが、この一覧が
                                // 見せたいのは「引取が残っている」ことである。
                                List.of("UNLOAD")))
                        .toList());
    }

    /** これから作業する航海と港（S02 荷役）。 */
    @QueryHandler
    public VoyagePortListView handle(FindVoyagePortsQuery query) {
        // **上限より 1 件多く引いて、切れたかどうかを判別する**（IT10 レビュー N6）。
        // 件数が上限ちょうどのときに「切れた」と言うと、警告が常時点灯して
        // 合図として働かなくなる。
        var rows = cargos.findVoyagePorts(VOYAGE_PORT_LIMIT + 1);
        boolean truncated = rows.size() > VOYAGE_PORT_LIMIT;
        return new VoyagePortListView(
                rows.stream()
                        .limit(VOYAGE_PORT_LIMIT)
                        .map(row -> new VoyagePortView(row.voyageNumber(), row.unlocode(),
                                row.cargoCount()))
                        .toList(),
                truncated);
    }
}
