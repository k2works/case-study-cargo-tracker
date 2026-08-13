package com.example.cargotracker.handling.domain.model.valueobjects;

/**
 * 訂正・取り消しの種別（US36）。
 *
 * <p><strong>戻す範囲が違う。</strong> 取り消しは輸送の状態を引取前に戻すが、
 * 訂正は記録の中身だけを直す。<strong>同じ「直す」でも、承認したときに
 * 起きることが違う</strong>ため、種別として持つ。
 */
public enum CorrectionRequestType {

    /** 取り消し。**承認すると貨物状態が引取前に戻る。** */
    CANCEL("取り消し"),

    /** 訂正。**記録の中身を直す。貨物状態は動かない。** */
    CORRECT("訂正");

    private final String displayName;

    CorrectionRequestType(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す日本語名。**列挙子名を利用者に見せない。** */
    public String displayName() {
        return displayName;
    }

    /** 承認したときに貨物状態を戻すか。**画面と処理は同じ述語を使う。** */
    public boolean revertsCargoStatus() {
        return this == CANCEL;
    }
}
