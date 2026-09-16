package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 例外への対応がどこまで進んだか（domain-model.md の要素表 / UC16）。
 *
 * <p><b>解決しても例外は消えない</b>（不変条件 6）。事実は残り、料金調整の
 * 根拠になる。消えるのは「対応が要る」という状態だけである。</p>
 */
public enum ResponseStatus {
    /** 起票された。まだ誰も着手していない。 */
    REPORTED("起票"),
    /** 対応中。担当が付き、荷主へ知らせた。 */
    RESPONDING("対応中"),
    /** 解決した。<b>貨物状態は例外前へ戻る</b>（不変条件 5）。 */
    RESOLVED("解決");

    private final String label;

    ResponseStatus(String label) {
        this.label = label;
    }

    /** 画面に出す呼び名。 */
    public String label() {
        return label;
    }

    /**
     * 決着しているか。
     *
     * <p>例外一覧（S42）は<b>既定でこれを外す</b>——決着したものが混ざると、
     * 一覧全体が「まだ手を入れる場所」に見えなくなる。</p>
     */
    public boolean settled() {
        return this == RESOLVED;
    }
}
