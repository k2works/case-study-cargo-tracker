package com.example.cargotracker.booking.domain.model.valueobjects;

import java.time.Instant;

/**
 * 引取にまつわる 3 つの値（US16 / US35 / IT13 レビュー C1）。
 *
 * <p><strong>3 つとも「引取」という 1 つの出来事を指している。</strong> 誰が受け取るか
 * （荷受人）、受け取ってよいと確かめる合鍵（引取確認コード）、実際に受け取った時刻。
 * ばらばらに持つと、集約が「引取」について知っていることが数え上げにくくなる。
 *
 * <p><strong>どれも未確定でありうる。</strong>
 *
 * <ul>
 *   <li>荷受人は<strong>予約の時点では決まっていないことがある</strong>。国際輸送では
 *       後から決まる。必須にすると、決まるまで予約を登録できなくなる</li>
 *   <li>引取確認コードは<strong>確定のときに採番する</strong>。列が無かったころに
 *       確定した予約は値を持たない</li>
 *   <li>引取日時は<strong>引取が済むまで無い</strong></li>
 * </ul>
 *
 * <p><strong>荷役の記録から導出しない。</strong> 導出すると、荷役の記録が訂正・削除された
 * ときに請求済みの引取日が黙って動く。
 *
 * @param code       引取確認コード（US35）。確定前・旧い行では {@code null}
 * @param consignee  荷受人（US16）。未確定なら {@code null}
 * @param claimedAt  引取が済んだ日時。引取前・旧い行では {@code null}
 */
public record CargoClaim(ClaimCode code, Consignee consignee, Instant claimedAt) {

    /** まだ何も決まっていない状態。 */
    public static CargoClaim none() {
        return new CargoClaim(null, null, null);
    }

    /** 荷受人だけを差し替える。 */
    public CargoClaim withConsignee(Consignee newConsignee) {
        return new CargoClaim(code, newConsignee, claimedAt);
    }

    /** 引取確認コードだけを差し替える。 */
    public CargoClaim withCode(ClaimCode newCode) {
        return new CargoClaim(newCode, consignee, claimedAt);
    }

    /** 引取が済んだ時刻を記録する。 */
    public CargoClaim claimedAt(Instant when) {
        return new CargoClaim(code, consignee, when);
    }

    /** 引取確認コードが採番済みか。 */
    public boolean hasCode() {
        return code != null;
    }
}
