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
     * <p><b>文字列で受ける。</b> 「空か」と「形式が正しいか」と「候補に含まれるか」を
     * ここ 1 か所で順に見る——呼ぶ側が先に {@code Location.of} を通すと、空のときに
     * 形式エラーが出て業務の言葉で断れない。</p>
     *
     * @param candidates 指定できる港（{@code DischargeCandidates} が作る）。
     *     <b>判定はこの 1 本だけを見る</b>——「現在地か、残りの寄港地か」を
     *     ここでもう一度組み立てると、画面の選択肢と食い違う余地が生まれる
     */
    public static CancellationDecision approve(String dischargeUnLocode,
            List<Location> candidates, String reason, String decidedBy, Instant decidedAt) {
        if (blankToNull(dischargeUnLocode) == null) {
            // **形式の検査より先に置く。** 呼ぶ側で `Location.of` を先に通すと、
            // 承認する人が読むのは「UN/LOCODE は英大文字 5 文字です: null」に
            // なる——業務の言葉で断れない（IT15 のレビュー 中）。
            throw new BusinessRuleViolation("陸揚げ地は必須です");
        }
        Location dischargeLocation = Location.of(dischargeUnLocode.trim());
        if (!candidates.contains(dischargeLocation)) {
            // **0 件の候補から選ばせない。** どこなら指定できるかを添える。
            throw new BusinessRuleViolation("陸揚げ地 " + dischargeLocation.unLocode().value()
                    + " は旅程にありません。指定できるのは " + describe(candidates) + " です");
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

    private static String describe(List<Location> candidates) {
        return candidates.isEmpty() ? "（ありません）" : candidates.stream()
                .map(port -> port.unLocode().value())
                .collect(java.util.stream.Collectors.joining("・"));
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
