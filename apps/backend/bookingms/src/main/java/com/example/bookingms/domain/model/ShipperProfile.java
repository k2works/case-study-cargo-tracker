package com.example.bookingms.domain.model;

/**
 * 荷主の連絡先。氏名/社名・メールアドレス・住所・電話番号で 1 組の意味を持つ。
 *
 * <p>いずれも「空でない」以外の不変条件を持たないため、値オブジェクトにはしない
 * （[ADR-012]）。まとめているのは、引数として並べると順番を取り違えても
 * すべて {@code String} で型が合ってしまうため。
 *
 * @param name 氏名または社名
 * @param email メールアドレス
 * @param address 住所
 * @param phone 電話番号（任意）
 */
public record ShipperProfile(String name, String email, String address, String phone) {
}
