package com.example.bookingms.domain.model.valueobjects;

/**
 * 荷主の連絡先。氏名/社名・メールアドレス・住所・電話番号で 1 組の意味を持つ。
 *
 * <p>氏名/社名・住所・電話番号は「空でない」以外の不変条件を持たないため、値オブジェクトに
 * はしない（[ADR-012]）。形式の不変条件を持つメールアドレスだけ {@link EmailAddress} にする。
 * まとめているのは、引数として並べると順番を取り違えても型が合ってしまうため。
 *
 * @param name 氏名または社名
 * @param email メールアドレス
 * @param address 住所
 * @param phone 電話番号（任意）
 */
public record ShipperProfile(String name, EmailAddress email, String address, String phone) {

    /** 検査済みでない文字列から組み立てる。ここで {@link EmailAddress#of} を通す。 */
    public static ShipperProfile of(String name, String email, String address, String phone) {
        return new ShipperProfile(name, EmailAddress.of(email), address, phone);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static ShipperProfile restore(
            String name, String email, String address, String phone) {
        return new ShipperProfile(name, EmailAddress.restore(email), address, phone);
    }
}
