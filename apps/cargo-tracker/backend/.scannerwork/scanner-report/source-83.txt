package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
/**
 * 連絡先メールアドレス。
 *
 * <p>システム全体での一意は集約 1 つでは守れないので、ここでは<b>形</b>だけを守る。
 * 一意は「登録前の存在確認 + 投影の UNIQUE + 拒否の記録」の三段（domain-model.md）。</p>
 */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;

    public Email {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation("メールアドレスは必須です");
        }
        if (value.length() > MAX_LENGTH) {
            throw new BusinessRuleViolation("メールアドレスが長すぎます: " + value.length() + " 文字");
        }
        if (!hasValidShape(value)) {
            throw new BusinessRuleViolation("メールアドレスの形式が正しくありません: " + value);
        }
    }

    /**
     * 形だけを見る。
     *
     * <p>正規表現を使わないのは、入れ子の量指定子（{@code (\.[^.]+)+}）が長い入力で
     * 総当たりに落ち、スタックを食い潰しうるためである。長さ検査で緩和はできるが、
     * <b>順序に頼る守りは並べ替えで静かに壊れる。</b></p>
     */
    private static boolean hasValidShape(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) {
            return false;
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);

        return hasNoWhitespace(local) && hasNoWhitespace(domain) && isValidDomain(domain);
    }

    /** ドメインは「.」で 2 つ以上に分かれ、どの部分も空でないこと。 */
    private static boolean isValidDomain(String domain) {
        if (domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNoWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
