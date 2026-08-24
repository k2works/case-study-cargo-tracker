package com.example.bookingms.domain.model;

/**
 * 陸揚げ地の候補 1 件（US30-5・[ADR-025] 決定 4）。
 *
 * <p><strong>なぜ候補なのかを添える。</strong>港の名前だけを並べると、追跡管理者は
 * どれを選べばよいか決められない。「いま貨物がある港」と「これから寄る港」では、
 * 荷降しできる時期も、荷主に伝える内容も違う。
 *
 * @param unLocode 港の UN/LOCODE
 * @param name 港の名前。<strong>画面に対訳表を置かせない</strong>
 * @param reason なぜ候補なのか（現在地の港 / 次の寄港地）
 */
public record DischargeCandidate(String unLocode, String name, String reason) {

    /** いま貨物がある港。最も早く降ろせる。 */
    static DischargeCandidate currentPort(com.example.shared.domain.model.Location location) {
        return new DischargeCandidate(location.unLocode(), location.name(), "現在地の港");
    }

    /** これから寄る港。旅程の残りの荷降し地である。 */
    static DischargeCandidate nextPort(com.example.shared.domain.model.Location location) {
        return new DischargeCandidate(location.unLocode(), location.name(), "次の寄港地");
    }
}
