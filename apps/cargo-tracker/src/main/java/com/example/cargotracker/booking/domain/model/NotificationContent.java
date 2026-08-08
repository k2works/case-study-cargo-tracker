package com.example.cargotracker.booking.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 荷主に伝える内容（US12）。
 *
 * <p><strong>料金は載せない。</strong> 概算式（ADR-008）は経路候補の並べ替え用であり、
 * 荷主に見せる金額として設計していない。見せた瞬間に請求額として読まれる。
 * 料金は US21（Release 2.0）で算出する。
 *
 * <p><strong>期限を延ばした場合はその差分を必ず載せる。</strong> 荷主が知る必要があるのは
 * 「いつ着くか」だけではなく、<strong>当初の約束から何日ずれたか</strong>である
 * （{@code ui_design.md} 経路割り当て §候補ゼロ時の再算出）。
 *
 * @param transitPorts    経由港（直行なら空）
 * @param transitDays     所要日数
 * @param estimatedArrival 到着予定日
 * @param trackingNumber  追跡番号。未発行なら {@code null}
 * @param voyageNumbers   航海番号（区間の順）
 * @param originalDeadline 当初の希望期限。延ばしていなければ {@code null}
 * @param extraDays       当初から延ばした日数。延ばしていなければ 0
 */
public record NotificationContent(
        List<String> transitPorts,
        long transitDays,
        LocalDate estimatedArrival,
        String trackingNumber,
        List<String> voyageNumbers,
        LocalDate originalDeadline,
        long extraDays) {

    public NotificationContent {
        transitPorts = transitPorts == null ? List.of() : List.copyOf(transitPorts);
        voyageNumbers = voyageNumbers == null ? List.of() : List.copyOf(voyageNumbers);
        if (voyageNumbers.isEmpty()) {
            // **送るべき中身が無い通知を作らせない。** 経路が確定していない予約への
            // 通知を「送信済み」として記録すると、履歴そのものが信用できなくなる
            throw new IllegalArgumentException("経路が確定していないため通知できません");
        }
        if (estimatedArrival == null) {
            throw new IllegalArgumentException("到着予定日は必須です");
        }
    }

    /** 期限を延ばしているか。 */
    public boolean deadlineRelaxed() {
        return extraDays > 0;
    }

    /**
     * 送る文面。
     *
     * <p><strong>組み立て直せるようにせず、送った文面そのものを記録する。</strong>
     * 経路や期限は後から変わるため、あとで組み立て直すと
     * 「送った内容」と違うものが表示される。
     */
    public String toMessage() {
        StringBuilder message = new StringBuilder();
        message.append("確定した経路をお知らせします。\n");
        message.append("航海番号: ").append(String.join(" → ", voyageNumbers)).append('\n');
        message.append("経由港: ")
                .append(transitPorts.isEmpty() ? "直行" : String.join(" → ", transitPorts))
                .append('\n');
        message.append("所要日数: ").append(transitDays).append(" 日\n");
        message.append("到着予定日: ").append(estimatedArrival).append('\n');
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            message.append("追跡番号: ").append(trackingNumber).append('\n');
        }
        if (deadlineRelaxed()) {
            message.append("当初の希望期限: ").append(originalDeadline)
                    .append("（").append(extraDays).append(" 日の延長をお願いしています）\n");
        }
        return message.toString();
    }
}
