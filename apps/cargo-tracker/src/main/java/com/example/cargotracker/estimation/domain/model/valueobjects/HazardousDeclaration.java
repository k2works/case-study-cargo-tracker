package com.example.cargotracker.estimation.domain.model.valueobjects;

/**
 * 危険物の申告（US01 の受入基準 6）。
 *
 * <p><strong>見積の段階では任意である。</strong> 予約（US05）では申告が無いと
 * 受け付けないが、見積は照会であり、荷主から情報が揃う前に概算を出すことがある。
 *
 * <p><strong>入れたものは保存して予約へ引き継ぐ。</strong> 入力させて捨てると、
 * 入れた人は保存されたと思う。
 *
 * @param hazardClass        危険物クラス
 * @param unNumber           UN 番号
 * @param properShippingName 正式輸送品名
 */
public record HazardousDeclaration(
        String hazardClass, String unNumber, String properShippingName) {

    /** 何も入っていないか。 */
    public boolean isEmpty() {
        return blank(hazardClass) && blank(unNumber) && blank(properShippingName);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 入力から作る。何も入っていなければ {@code null}。
     *
     * <p><strong>空の申告を持たない。</strong> 「申告がある」と「空の申告がある」は
     * 画面で区別できず、読む人を迷わせる。
     */
    public static HazardousDeclaration of(
            String hazardClass, String unNumber, String properShippingName) {
        HazardousDeclaration declaration =
                new HazardousDeclaration(hazardClass, unNumber, properShippingName);
        return declaration.isEmpty() ? null : declaration;
    }
}
