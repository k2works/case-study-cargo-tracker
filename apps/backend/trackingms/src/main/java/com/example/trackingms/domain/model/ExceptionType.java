package com.example.trackingms.domain.model;

import java.util.Arrays;
import java.util.List;

/**
 * 追跡の例外の種別（US19・US20・[ADR-024] 決定 3・11）。
 *
 * <p><strong>要件は種別そのものが持つ。</strong>呼び出し側に {@code if (type == LOST)} を
 * 書かせると、種別が増えたときに書き換える場所が散らばり、書き換え忘れた場所だけが
 * 黙って通す（[ADR-023] 決定 1 と同じ形）。
 */
public enum ExceptionType {

    /** 遅延。天候・港湾の混雑など。 */
    DELAY("遅延", true, false),

    /** 破損。外装や中身の破損。 */
    DAMAGE("破損", true, false),

    /**
     * 紛失。所在が確認できない。
     *
     * <p><strong>これだけが緊急である。</strong>貨物が見つからないことは、遅れや破損とは
     * 別の重さを持つ——他は届く見込みがあるが、これは無い。
     */
    LOST("紛失", true, true),

    /**
     * 誤配。予定ルート外の荷役から<strong>自動で</strong>検知する（US28・IT10）。
     *
     * <p><strong>手では起票できない。</strong>手で起票できると、自動の検知と人の起票が
     * 混ざり、どちらが正しいかを誰も判断できなくなる。
     */
    MISROUTE("誤配", false, false),

    /**
     * 税関保留。通関申告が起票する（US29・IT9）。
     *
     * <p><strong>手では起票できない。</strong>理由は {@link #MISROUTE} と同じ。
     */
    CUSTOMS_HOLD("税関保留", false, false);

    private final String label;
    private final boolean raisableByOperator;
    private final boolean urgent;

    ExceptionType(String label, boolean raisableByOperator, boolean urgent) {
        this.label = label;
        this.raisableByOperator = raisableByOperator;
        this.urgent = urgent;
    }

    /** 画面に出す名前。<strong>画面が対訳表を持たない</strong>（[ADR-023] 決定 1 と同じ形）。 */
    public String label() {
        return label;
    }

    /** 追跡管理者・荷役作業員が手で起票できるか（[ADR-024] 決定 11）。 */
    public boolean raisableByOperator() {
        return raisableByOperator;
    }

    /** 緊急として扱うか（[ADR-024] 決定 3）。 */
    public boolean urgent() {
        return urgent;
    }

    /** 手で起票できる種別。<strong>画面はこれを受け取り、一覧を持たない</strong>。 */
    public static List<ExceptionType> raisableTypes() {
        return Arrays.stream(values()).filter(ExceptionType::raisableByOperator).toList();
    }

    /**
     * 入力された名前から種別を読む。<strong>読み方はここだけが持つ</strong>。
     *
     * <p>入口が増えるたびに {@code valueOf} を書くと、入口ごとに不正の見え方が変わる
     * （返済枠 0.5 と同じ形）。
     *
     * @throws IllegalArgumentException 空、語彙に無い名前、または手では起票できない種別
     */
    public static ExceptionType parseRaisable(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("例外の種別を選んでください");
        }
        ExceptionType type;
        try {
            type = valueOf(name);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("例外の種別が不正です: " + name);
        }
        if (!type.raisableByOperator) {
            throw new IllegalArgumentException(
                    "%s は自動で検知されるため、手では起票できません".formatted(type.label));
        }
        return type;
    }

    /** 永続化された行から復元する。ここでは起票できるかを問わない。 */
    public static ExceptionType restore(String name) {
        if (name == null) {
            throw new IllegalStateException("例外の種別の無い行を読み込みました");
        }
        return valueOf(name);
    }
}
