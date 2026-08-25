package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.RouteCandidateFinder;
import com.example.bookingms.application.port.RouteCandidateQuery;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.RouteSpecification;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 選んだ経路を予約に割り当てる（US09 / US11・[ADR-019]・[ADR-020]）。
 *
 * <p>ここがやるのは 3 つである。**選んだ経路がまだ成立するか**を確かめ、集約に割り当てさせ、
 * 保存する。状態遷移と要件の検査は集約が持つ（ここに書き足すと、判定が 2 か所になる）。
 */
public class AssignRouteUseCase {

    private final CargoRepository cargoes;
    private final LocationRepository locations;
    private final RouteCandidateFinder routeCandidates;

    public AssignRouteUseCase(CargoRepository cargoes, LocationRepository locations,
            RouteCandidateFinder routeCandidates) {
        this.cargoes = cargoes;
        this.locations = locations;
        this.routeCandidates = routeCandidates;
    }

    /**
     * 割り当てる。予約が見つからなければ空を返す。
     *
     * @param maxTransshipments 候補を出したときに使った積み替えの上限（US10 で緩めた値）。
     *     再検証を同じ条件で行うために受け取る。渡さないと、緩めた条件で選んだ経路が
     *     「候補に無い」と判定され、画面には出たのに確定できない
     * @throws RouteNoLongerAvailableException 選んだ経路がもう成立しないとき（[ADR-019] 決定 2）
     */
    public Optional<AssignmentResult> assign(String bookingId, CargoItinerary chosen,
            Integer maxTransshipments) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> {
                    requireStillAvailable(cargo, chosen, maxTransshipments);
                    ZoneId destinationZone = destinationZoneOf(cargo);
                    // **誤配のあとは別の操作である**（[ADR-026] 決定 4b）。
                    // 通常の割り当てを通すと、輸送中の貨物が「経路を提示した」状態へ戻り、
                    // 荷主が合意して確定した記録が消える
                    Cargo assigned = cargoes.save(cargo.isMisrouted()
                            ? cargo.reassignItinerary(chosen)
                            : cargo.assignItinerary(chosen, destinationZone));
                    // **期限を超えるなら、何日超えるかを返す**（US28-6・[ADR-026] 決定 5）。
                    // 「間に合いません」だけでは、荷主は次の手を決められない
                    return new AssignmentResult(assigned,
                            assigned.daysBeyondDeadline(destinationZone).orElse(null));
                });
    }

    /**
     * 選んだ経路がまだ成立するか（[ADR-019] 決定 2）。
     *
     * <p>候補を出してから確定するまでのあいだに、航海が更新・欠航・削除されることがある。
     * 確かめずに通すと<strong>欠航した航海の旅程が予約に入る</strong>。荷役の担当者は来ない船を
     * 待ち、荷主には出ない便の予定が伝わる。しかも間違いに気づくのは出港予定日である。
     *
     * <p>断り方は 409 相当（{@link RouteNoLongerAvailableException}）にする。入力の形式は正しく、
     * 直すべきは入力ではなく「経路をもう一度探すこと」である。<strong>専用の型にするのは、
     * こちら側の不備まで同じ断り方に混ざらないようにするため</strong>である。
     */
    private void requireStillAvailable(Cargo cargo, CargoItinerary chosen,
            Integer maxTransshipments) {
        RouteSpecification route = cargo.routeSpecification();
        // **誤配のあとは現在地が出発地である**（US28-4・[ADR-026] 決定 4）。
        // 元の出発地で候補を引くと、画面に出した候補（現在地起点）が
        // 「候補に無い」と判定され、**選べたのに確定できない**
        boolean reroute = cargo.isMisrouted();
        String origin = reroute
                ? cargo.lastHandlingLocation().orElse(route.origin().unLocode())
                : route.origin().unLocode();
        // **再設計では期限で弾かないことを相手に伝える**（[ADR-026] 決定 4）。
        // 集約から期限検査を外しただけでは足りない——routingms は既定で期限を超える
        // 候補を刈るため、伝えなければ**候補が 1 本も返らず組み直せない**
        List<CargoItinerary> available = routeCandidates.find(new RouteCandidateQuery(
                origin, route.destination().unLocode(),
                route.arrivalDeadline(), cargo.type(), maxTransshipments,
                route.departureDate().orElse(null), reroute));

        if (!available.contains(chosen)) {
            throw new RouteNoLongerAvailableException(
                    "選んだ経路はもう使えません。航海スケジュールが変わっている可能性があります。"
                            + "経路をもう一度探してください");
        }
    }

    /**
     * 到着期限の「当日」は目的地の暦で決まる（[ADR-010]）。
     *
     * <p>UTC で判断すると、時差の分だけ当日が短くなり、期限当日の遅い時刻に着く経路が
     * 黙って断られる。
     *
     * <p>マスタに無いのは<strong>こちら側の不備</strong>である。利用者に「もう一度探して」と
     * 促しても直らない（{@link LocationMasterMissingException}）。
     */
    private ZoneId destinationZoneOf(Cargo cargo) {
        return locations.timeZoneOf(cargo.routeSpecification().destination().unLocode())
                .orElseThrow(() -> new LocationMasterMissingException(
                        cargo.routeSpecification().destination().unLocode()));
    }
    /**
     * 割り当ての結果。
     *
     * @param cargo 割り当て後の予約
     * @param daysBeyondDeadline 到着予定が希望期限を超える日数。超えないなら {@code null}
     */
    public record AssignmentResult(Cargo cargo, Long daysBeyondDeadline) {
    }

}
