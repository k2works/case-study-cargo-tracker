package com.example.cargotracker.shared.application.logging;

/**
 * 業務操作ログに出す値を安全な形に整える。
 *
 * <p><strong>利用者が入力した値をそのままログに書いてはならない。</strong>
 * 荷主名に改行を含めれば、ログの 1 行を 2 行に割り、後続に偽の行を差し込める。
 * 監査ログは「何が起きたか」の証拠であり、**利用者が内容を書ける監査ログは
 * 証拠として使えない**（ログインジェクション）。
 *
 * <p>あわせて長さを制限する。名称欄に数百文字を入れてログを埋め尽くす操作を防ぐ。
 */
public final class AuditValue {

    /** ログに出す 1 項目の上限。業務上の名称・メールはこの範囲に収まる。 */
    private static final int MAX_LENGTH = 100;

    private AuditValue() {
    }

    /**
     * 改行・制御文字を取り除き、長さを制限する。
     *
     * @param value 利用者が入力しうる値
     * @return ログに出してよい形にした値。{@code null} は {@code "-"}
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "_");
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH) + "…";
        }
        return cleaned;
    }
}
