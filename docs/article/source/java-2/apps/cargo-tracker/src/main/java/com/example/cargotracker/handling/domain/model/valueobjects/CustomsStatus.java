package com.example.cargotracker.handling.domain.model.valueobjects;

/**
 * 通関状態（US29。{@code domain-model.md}）。
 *
 * <p><strong>通関は業務上の唯一の「止まる仕組み」である。</strong> 誤配も荷受人違いも
 * 「起きた事実」として記録するが、<strong>通関前の引き渡しは実行してはならない</strong>。
 */
public enum CustomsStatus {

    /** 審査中。申告を出した直後の状態。 */
    PENDING("審査中"),

    /** 通関済。**この状態でのみ引取ができる。** */
    CLEARED("通関済"),

    /** 留置。書類不備・検査などで税関に止められている。**保管料が発生する。** */
    HELD("留置"),

    /** 不可。通関が認められなかった。 */
    REJECTED("不可");

    private final String displayName;

    CustomsStatus(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す日本語名（正典は {@code ui_design.md}）。 */
    public String displayName() {
        return displayName;
    }

    /**
     * 引取を許すか。
     *
     * <p><strong>状態自身が持つ。</strong> 呼び出し側で「CLEARED なら」と書くと、
     * 状態が増えたときに片方だけが更新される。
     */
    public boolean allowsClaim() {
        return this == CLEARED;
    }

    /** 放置するとコストが発生する状態か（一覧の警告色・ダッシュボードの件数）。 */
    public boolean costsWhileWaiting() {
        return this == HELD;
    }

    /**
     * 追跡担当者の対応が要る状態か（例外として起票する対象）。
     *
     * <p><strong>不可は留置より重い。</strong> 留置は保管料だが、不可は積戻し・廃棄・
     * 関税の争いに発展する。留置だけを起票の対象にすると、
     * <strong>最も重い状態が最も静かになる</strong>。
     */
    public boolean needsAttention() {
        return this == HELD || this == REJECTED;
    }
}
