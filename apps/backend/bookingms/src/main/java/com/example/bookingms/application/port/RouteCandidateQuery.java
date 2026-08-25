package com.example.bookingms.application.port;

import com.example.bookingms.domain.model.CargoType;
import java.time.LocalDate;

/**
 * 経路候補を頼むときの条件（US09 / US10）。
 *
 * <p>{@code maxTransshipments} を渡せるようにしているのは、候補が出なかったときに
 * 経路設計者が条件を緩めるためである（US10）。渡せないと、画面で緩めた条件が
 * 割り当ての再検証に届かず、「画面には出たのに確定できない」が起きる。
 *
 * @param originUnLocode 出発地。貨物の現在地を起点にした再設計（US28）も同じ入口を使う
 * @param destinationUnLocode 目的地
 * @param arrivalDeadline 到着期限。<strong>日付で渡す</strong>（[ADR-017] 決定 3）
 * @param cargoType 貨物種別。運べる船が限られる
 * @param maxTransshipments 積み替えの上限。{@code null} なら相手側の既定値
 * @param earliestDeparture 出発希望日。{@code null} なら出発の早さでは絞らない
 * @param reroute 誤配のあとの組み直しか（US28-4・[ADR-026] 決定 4）。
 *     <strong>期限で候補を弾かないことを相手に伝える</strong>——伝えなければ、
 *     期限に間に合う便が残っていない誤配貨物には候補が 1 本も返らない
 */
public record RouteCandidateQuery(
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        CargoType cargoType,
        Integer maxTransshipments,
        LocalDate earliestDeparture,
        boolean reroute) {
}
