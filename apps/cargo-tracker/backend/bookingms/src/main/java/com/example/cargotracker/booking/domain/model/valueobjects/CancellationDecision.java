package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.util.List;

/**
 * キャンセル申請への判断（US30 §受入基準 5・7 / 不変条件 9-2）。
 *
 * <p><b>承認には陸揚げ地が要り、却下には要らない。</b> 承認とは「どこで降ろすか」を
 * 決めることであり、決めずに承認しても貨物は船の上に残る。却下は輸送を続ける
 * 判断なので、降ろす港は無い。</p>
 *
 * <p><b>陸揚げ地は現在地か残りの寄港地に限る</b>（不変条件 9-2）。旅程に無い港を
 * 指定すると、手配できない港で荷降しを待つことになる。通過済みの港も同じで、
 * 船はもうそこに戻らない。</p>
 *
 * @param dischargeLocation 陸揚げ地。<b>却下では {@code null}</b>
 * @param reason 判断の理由。<b>却下では必須</b>——理由の無い却下は、申請した営業が
 *     次に何をすればよいか分からない。承認では任意（陸揚げ地が理由を語る）
 */
public record CancellationDecision(
        boolean approved,
        Location dischargeLocation,
        String reason,
        String decidedBy,
        Instant decidedAt) {

    /**
     * 承認する。
     *
     * @param current 現在地（最後の荷役の港）。<b>まだ荷役が無ければ {@code null}</b>
     * @param remainingPorts 旅程の残りの寄港地
     */
    public static CancellationDecision approve(Location dischargeLocation, Location current,
            List<Location> remainingPorts, String reason, String decidedBy, Instant decidedAt) {
        if (dischargeLocation == null) {
            throw new BusinessRuleViolation("陸揚げ地は必須です");
        }
        boolean reachable = dischargeLocation.equals(current)
                || remainingPorts.contains(dischargeLocation);
        if (!reachable) {
            // **0 件の候補から選ばせない。** どこなら指定できるかを添える。
            throw new BusinessRuleViolation("陸揚げ地 " + dischargeLocation.unLocode().value()
                    + " は旅程にありません。指定できるのは "
                    + describe(current, remainingPorts) + " です");
        }
        return new CancellationDecision(true, dischargeLocation, blankToNull(reason),
                required(decidedBy), decidedAt);
    }

    /** 却下する。<b>理由は必須</b>——申請した営業が次に何をすればよいか分からない。 */
    public static CancellationDecision reject(String reason, String decidedBy,
            Instant decidedAt) {
        if (blankToNull(reason) == null) {
            throw new BusinessRuleViolation("却下の理由は必須です");
        }
        return new CancellationDecision(false, null, reason.trim(), required(decidedBy),
                decidedAt);
    }

    private static String describe(Location current, List<Location> remainingPorts) {
        var candidates = new java.util.LinkedHashSet<String>();
        if (current != null) {
            candidates.add(current.unLocode().value());
        }
        remainingPorts.forEach(port -> candidates.add(port.unLocode().value()));
        return candidates.isEmpty() ? "（ありません）" : String.join("・", candidates);
    }

    /** 空文字は {@code null} に寄せる（入口で正規化する。判定を 2 か所に置かない）。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String decidedBy) {
        if (blankToNull(decidedBy) == null) {
            throw new BusinessRuleViolation("判断した人は必須です");
        }
        return decidedBy.trim();
    }
}
