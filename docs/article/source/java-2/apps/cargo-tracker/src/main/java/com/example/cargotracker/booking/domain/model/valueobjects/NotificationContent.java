package com.example.cargotracker.booking.domain.model.valueobjects;

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
 * @param itinerary 決まった経路
 * @param deadline  期限に対する結果
 * @param trackingNumber  追跡番号。未発行なら {@code null}
 */
public record NotificationContent(
        Itinerary itinerary,
        String trackingNumber,
        Deadline deadline) {

    /**
     * 決まった経路。
     *
     */
    public record Itinerary(
            List<String> transitPorts,
            long transitDays,
            LocalDate estimatedArrival,
            List<String> voyageNumbers) {

        public Itinerary {
            transitPorts = transitPorts == null ? List.of() : List.copyOf(transitPorts);
            voyageNumbers = voyageNumbers == null ? List.of() : List.copyOf(voyageNumbers);
        }
    }

    /**
     * 期限に対する結果（US10 / US28）。
     *
     * @param original         当初の希望到着期限
     */
    public record Deadline(LocalDate original, long extraDays, long daysOverDeadline) { }

    // --- 呼び出し側が使う名前（委譲するアクセサ）---

    /** @return 経由港 */
    public List<String> transitPorts() {
        return itinerary.transitPorts();
    }

    /** @return 所要日数 */
    public long transitDays() {
        return itinerary.transitDays();
    }

    /** @return 到着予定日 */
    public LocalDate estimatedArrival() {
        return itinerary.estimatedArrival();
    }

    /** @return 航海番号 */
    public List<String> voyageNumbers() {
        return itinerary.voyageNumbers();
    }

    /** @return 当初の希望到着期限 */
    public LocalDate originalDeadline() {
        return deadline.original();
    }

    /** @return 延ばした日数 */
    public long extraDays() {
        return deadline.extraDays();
    }

    /** @return 当初の期限を超える日数 */
    public long daysOverDeadline() {
        return deadline.daysOverDeadline();
    }


    public NotificationContent {
        if (itinerary.voyageNumbers().isEmpty()) {
            // **送るべき中身が無い通知を作らせない。** 経路が確定していない予約への
            // 通知を「送信済み」として記録すると、履歴そのものが信用できなくなる
            throw new IllegalArgumentException("経路が確定していないため通知できません");
        }
        if (itinerary.estimatedArrival() == null) {
            throw new IllegalArgumentException("到着予定日は必須です");
        }
    }

    /** 期限を延ばしているか。 */
    public boolean deadlineRelaxed() {
        return deadline.extraDays() > 0;
    }

    /** 到着予定が希望期限を超えるか（US28）。 */
    public boolean overshootsDeadline() {
        return deadline.daysOverDeadline() > 0;
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
        message.append("航海番号: ").append(String.join(" → ", itinerary.voyageNumbers())).append('\n');
        message.append("経由港: ")
                .append(itinerary.transitPorts().isEmpty()
                        ? "直行" : String.join(" → ", itinerary.transitPorts()))
                .append('\n');
        message.append("所要日数: ").append(itinerary.transitDays()).append(" 日\n");
        message.append("到着予定日: ").append(itinerary.estimatedArrival()).append('\n');
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            message.append("追跡番号: ").append(trackingNumber).append('\n');
        }
        if (deadlineRelaxed()) {
            message.append("当初の希望期限: ").append(deadline.original())
                    .append("（").append(deadline.extraDays()).append(" 日の延長をお願いしています）\n");
        }
        // **何日遅れるのかを書く**（US28）。「遅れます」だけでは、荷主は
        // 受け入れるか手配し直すかを判断できない
        if (overshootsDeadline()) {
            message.append("ご希望の期限より ").append(deadline.daysOverDeadline())
                    .append(" 日遅れる見込みです。あらためてご相談させてください。\n");
        }
        return message.toString();
    }
}
