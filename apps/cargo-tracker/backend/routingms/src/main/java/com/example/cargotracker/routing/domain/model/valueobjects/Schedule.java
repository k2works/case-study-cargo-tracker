package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.List;

/**
 * 航海スケジュール（Voyage 不変条件 2）。
 *
 * <p>寄港地は上から順に回る。連続する移動の到着港と次の出発港は同じでなければならず、
 * 時刻も昇順でなければならない。<b>この 2 つを分けて見る。</b> 片方だけを見ると、
 * 港は繋がっているが前の便より早く出る航海（実際には乗り継げない）が通る。</p>
 */
public record Schedule(List<CarrierMovement> movements) {

    public Schedule {
        if (movements == null || movements.isEmpty()) {
            throw new BusinessRuleViolation("寄港地を 1 件以上入力してください");
        }
        movements = List.copyOf(movements);
        for (int i = 1; i < movements.size(); i++) {
            CarrierMovement previous = movements.get(i - 1);
            CarrierMovement current = movements.get(i);
            if (!previous.arrival().equals(current.departure())) {
                throw new BusinessRuleViolation(
                        "寄港地が繋がっていません: " + previous.arrival().unLocode().value()
                                + " に着いたあと " + current.departure().unLocode().value()
                                + " から出ることはできません");
            }
            if (current.departureTime().isBefore(previous.arrivalTime())) {
                throw new BusinessRuleViolation(
                        "寄港地の時刻が前後しています: " + previous.arrivalTime()
                                + " に着く前の " + current.departureTime() + " に出ることはできません");
            }
        }
    }

    /** 最初の出発地（一覧の検索用に投影へ非正規化する）。 */
    public CarrierMovement first() {
        return movements.get(0);
    }

    /** 最後の到着地。 */
    public CarrierMovement last() {
        return movements.get(movements.size() - 1);
    }
}
