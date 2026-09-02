package com.example.handlingms.domain.model.valueobjects;

/**
 * 通関状態（UC21）。
 *
 * <p><strong>読み方はここだけが持つ</strong>（{@code ExceptionType#parseRaisable} と同じ形）。
 * 入口が増えるたびに {@code valueOf} を書くと、入口ごとに不正の見え方が変わる。
 */
public enum CustomsStatus {

    /** 申告を出し、税関の審査を待っている。**登録直後は必ずこれ**である。 */
    PENDING("審査中"),

    /** 通関が下りた。**引取（CLAIM）を許すのはこの状態だけ**である。 */
    CLEARED("通関済"),

    /** 税関に留め置かれている。3 日を超えたら督促の対象になる（US29-6）。 */
    HELD("留置"),

    /** 通関が認められなかった。書類を直して出し直せる（[ADR-025] 決定 7）。 */
    REJECTED("不可");

    private final String label;

    CustomsStatus(String label) {
        this.label = label;
    }

    /** 画面に出す名前。**画面が対訳表を持たない**（[ADR-023] 決定 1 と同じ形）。 */
    public String label() {
        return label;
    }

    /**
     * これ以上の判断を待たなくてよい状態か（[ADR-025] 決定 7）。
     *
     * <p>未決着（審査中・留置）が高々 1 件であることを、登録側がこれで守る。
     */
    public boolean settled() {
        return this == CLEARED || this == REJECTED;
    }

    /**
     * 入力された名前から状態を読む。
     *
     * @throws IllegalArgumentException 空、または語彙に無い名前
     */
    public static CustomsStatus parse(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("通関状態を選んでください");
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("通関状態が不正です: " + name);
        }
    }

    /** 永続化された行から復元する。**ここでは選べるかを問わない**（既存行を壊さない）。 */
    public static CustomsStatus restore(String name) {
        if (name == null) {
            throw new IllegalStateException("通関状態の無い行を読み込みました");
        }
        return valueOf(name);
    }
}
