package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 契約が運んでくる通関状態を、追跡から見た意味に写す（US29 §受入基準 5）。
 *
 * <p><b>handlingms の {@code CustomsStatus} を持ち込まない。</b> BC が違えば型も
 * 違う（ADR-0001 決定 4）。追跡が知りたいのは「税関保留を起票するのか、解決するのか」
 * だけで、通関の手続きそのものではない。</p>
 *
 * <p><b>判定は列挙が答える。</b> 呼び出し側に {@code if (status.equals("HELD"))} を
 * 書かせると、状態が増えたときに書き換える場所が散らばる。</p>
 *
 * <p><b>解決するかは「前の状態と新しい状態の対」で決まる。</b> 新しい状態だけを
 * 見ると、一度も留置にならず通関済になった申告（審査中 → 通関済）でも「解決する」
 * と判定してしまう——<b>起票していない例外は解決できない</b>ので集約が断り、
 * その例外が退避されて後続のイベントまで止まる（IT12 のクラスタ E2E で実測）。</p>
 *
 * <p><b>知らない状態は断る。</b> 素通りさせると、契約に値が増えたときに
 * 「起票も解決もされない」が静かに起きる——載せ忘れたものほど漏れる。断れば
 * 退避先に出る（[ADR-0014]）ので、気づける。</p>
 */
public enum CustomsHoldStatus {

    /** 審査中。まだ何も起きていない。 */
    PENDING("審査中", false),
    /** 通関済。 */
    CLEARED("通関済", false),
    /** 留置。税関保留を起票する。 */
    HELD("留置", true),
    /** 不可。通らなかったことは別の業務で扱う。 */
    REJECTED("不可", false);

    private final String label;
    private final boolean raisesHold;

    CustomsHoldStatus(String label, boolean raisesHold) {
        this.label = label;
        this.raisesHold = raisesHold;
    }

    /** 契約が運ぶ文字列から引く。 */
    public static CustomsHoldStatus of(String status) {
        // null は先に見る。NullPointerException を捕まえて意味を変えると、
        // 本当の実装ミスまで「知らない状態」に化ける。
        if (status == null) {
            throw new BusinessRuleViolation("通関状態がありません");
        }
        try {
            return valueOf(status);
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleViolation("知らない通関状態です: " + status);
        }
    }

    /** 利用者に見せる呼び名（domain-model.md の要素表が正典）。 */
    public String label() {
        return label;
    }

    /** 税関保留を起票するか。 */
    public boolean raisesHold() {
        return raisesHold;
    }

    /**
     * この遷移で税関保留を解決するか。
     *
     * <p><b>起票の後段を数えた結果である</b>（Try T1）。例外中の貨物は荷役を預かって
     * 適用しないので、解決しないと通関済にしても引取が届かない。</p>
     *
     * <p><b>前の状態が留置だったときだけ解決する。</b> 税関保留は留置のあいだしか
     * 存在しないので、留置を経ていない申告には解決する対象が無い。新しい状態だけで
     * 判定すると、起票していない例外を解決しようとして集約に断られ、その例外が
     * 退避されて後続まで止まる（IT12 のクラスタ E2E で実測）。</p>
     *
     * @param previous 変更前の状態。読めない値なら解決しない（安全側）
     */
    public static boolean resolvesHold(String previous, CustomsHoldStatus next) {
        return HELD.name().equals(previous) && next != HELD;
    }
}
