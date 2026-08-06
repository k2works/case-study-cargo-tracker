package com.example.cargotracker.shipper.domain.model;

/**
 * 荷主の連絡先。メールアドレス・電話番号・住所をひとまとまりで扱う。
 *
 * <p>US32 が訂正の対象とするのはこの 3 項目と荷主名である。**個別の引数として
 * 持ち回ると、同じ型の項目どうしで順序を取り違えても気づけない。**
 *
 * @param email   メールアドレス（必須）
 * @param phone   電話番号（任意。{@code null} は空として扱う）
 * @param address 住所（必須）
 */
public record ShipperContact(Email email, Phone phone, Address address) {

    public ShipperContact {
        if (email == null) {
            throw new IllegalArgumentException("メールアドレスは必須です");
        }
        if (address == null) {
            throw new IllegalArgumentException("住所は必須です");
        }
        phone = phone == null ? Phone.empty() : phone;
    }
}
