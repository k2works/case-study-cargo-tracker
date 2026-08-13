package com.example.cargotracker.handling.domain.model.valueobjects;

import java.util.Locale;

/**
 * 荷受人確認（US16）。
 *
 * <p><strong>引き渡し証明は事故時の唯一の防御線である</strong>（{@code ui_design.md}）。
 * 「渡した」「受け取っていない」の争いになったとき、確認の記録が無ければ会社が負う。
 *
 * <p><strong>方法・コード・荷受人氏名をひと組で持つ。</strong> 別々に持つと
 * 「コードはあるが誰が受け取ったか分からない」記録を作れる。
 *
 * @param method        確認の方法
 * @param code          確認コード（荷受人へ事前送付したもの）
 * @param consigneeName 実際に受け取った人の氏名
 */
public record ClaimConfirmation(
        ClaimConfirmationMethod method,
        String code,
        String consigneeName) {

    public ClaimConfirmation {
        if (method == null) {
            throw new IllegalArgumentException("確認の方法は必須です");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("確認コードは必須です");
        }
        if (consigneeName == null || consigneeName.isBlank()) {
            throw new IllegalArgumentException("荷受人氏名は必須です");
        }
        code = code.strip();
        consigneeName = consigneeName.strip();
    }

    /** 確認コードで確認する。 */
    public static ClaimConfirmation byCode(String code, String consigneeName) {
        return new ClaimConfirmation(
                ClaimConfirmationMethod.CONFIRMATION_CODE, code, consigneeName);
    }

    /**
     * 予約に登録された荷受人と一致するか。
     *
     * <p><strong>一致しなくても登録は拒否しない。</strong> 代理受領は実務で
     * 頻繁に起きる。伝えるのは警告であり、メモへの理由記入を求める。
     *
     * <p><strong>予約に荷受人が無ければ照合しない。</strong> 「違う」と言えるのは、
     * 比べる相手があるときだけである。無いことを「不一致」と呼ぶと、
     * 荷受人を登録していない予約すべてに警告が出て、警告そのものが読まれなくなる。
     *
     * @param registeredConsigneeName 予約に登録された荷受人氏名。無ければ {@code null}
     */
    public boolean matchesConsignee(String registeredConsigneeName) {
        if (registeredConsigneeName == null || registeredConsigneeName.isBlank()) {
            return true;
        }
        return normalize(consigneeName).equals(normalize(registeredConsigneeName));
    }

    /** 前後の空白と大小文字の差を無視する（氏名の表記ゆれで警告を出さない）。 */
    private static String normalize(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
