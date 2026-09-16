package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.time.LocalDate;

/**
 * 予約を受け付ける・直すときの入力の検査（{@link Cargo} から切り出したもの）。
 *
 * <p><b>状態の判断はここに置かない。</b> 「どの状態なら何ができるか」は
 * {@code BookingStatus}・{@code RoutingStatus} の述語が持ち、集約がそれを呼ぶ。
 * ここにあるのは入力そのものの形（欠けていないか・過去の日付でないか）だけである。</p>
 */
final class CargoValidation {

    private CargoValidation() {
    }

    static void validate(BookCargoCommand command, LocalDate today) {
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            throw new BusinessRuleViolation("予約 ID は必須です");
        }
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            // 荷主の分からない予約は、通知も請求も宛先が無い。
            throw new BusinessRuleViolation("荷主 ID は必須です");
        }
        validate(command.cargoSpecification(), command.routeSpecification(), today, null);
    }

    /**
     * 受付と修正で同じ検査を通す。分けて書くと片方だけが古くなる。
     *
     * <p>{@code currentDeadline} は据え置きを見分けるためのもの（受付では null）。
     * 期限を動かさない修正は、その期限が過去でも通す。</p>
     */
    static void validate(CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification, LocalDate today, LocalDate currentDeadline) {
        if (cargoSpecification == null) {
            throw new BusinessRuleViolation("貨物仕様は必須です");
        }
        if (routeSpecification == null) {
            throw new BusinessRuleViolation("輸送条件は必須です");
        }
        // 期限は日付で比較する。当日着は間に合う扱い（不変条件 5）。
        //
        // **新規の受け付けでだけ検査する。** 復元（@EventSourcingHandler）では見ない。
        // 見ると、受け付けたあとに期限を過ぎた予約が読めなくなる。
        if (routeSpecification.arrivalDeadline().equals(currentDeadline)) {
            return;
        }
        if (routeSpecification.arrivalDeadline().isBefore(today)) {
            throw new BusinessRuleViolation(
                    "到着期限が過去の日付です: " + routeSpecification.arrivalDeadline());
        }
    }
}
