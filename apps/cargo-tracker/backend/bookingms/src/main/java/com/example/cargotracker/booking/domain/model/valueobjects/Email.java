package com.example.cargotracker.booking.domain.model.valueobjects;

import java.util.regex.Pattern;

/**
 * 連絡先メールアドレス。
 *
 * <p>システム全体での一意は集約 1 つでは守れないので、ここでは<b>形</b>だけを守る。
 * 一意は「登録前の存在確認 + 投影の UNIQUE + 拒否の記録」の三段（domain-model.md）。</p>
 */
public record Email(String value) {

    // 総当たりで壊れない形にする（入れ子の量指定子を避ける）。
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("メールアドレスは必須です");
        }
        if (value.length() > 254) {
            throw new IllegalArgumentException("メールアドレスが長すぎます: " + value.length() + " 文字");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("メールアドレスの形式が正しくありません: " + value);
        }
    }
}
