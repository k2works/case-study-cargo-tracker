package com.example.cargotracker.booking.domain.model;

/**
 * 荷受人（US16）。
 *
 * <p>貨物を受け取る主体。<strong>引取時の本人確認に用いる</strong>のが最初の使い道であり、
 * それが本値オブジェクトを US04（予約登録）ではなく US16 で導入した理由である
 * （{@code user_story.md}）。予約の時点では未確定でありうる。
 *
 * <p><strong>3 項目とも素の文字列で持つ</strong>（{@code domain-model.md} の定義）。
 * Shipper Context の {@code Email}・{@code Address} を参照しない — 別の
 * 境界付けられたコンテキストの型であり、参照すると BC 分離に反する。
 * 荷受人は本システムの利用者ではなく、荷主のように登録・管理される対象でもない。
 *
 * <p><strong>氏名だけを必須とする。</strong> 住所とメールは、引き渡しの当日までに
 * 分かれば足りる。必須にすると、氏名しか分かっていない段階で登録できなくなり、
 * <strong>結局どこにも記録されない</strong>。
 *
 * @param name         荷受人の氏名または社名
 * @param address      住所。未確定なら {@code null}
 * @param contactEmail 連絡先メールアドレス。未確定なら {@code null}
 */
public record Consignee(String name, String address, String contactEmail) {

    public Consignee {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("荷受人氏名は必須です");
        }
        name = name.strip();
        address = blankToNull(address);
        contactEmail = blankToNull(contactEmail);
    }

    /** 氏名だけで登録する（住所・連絡先は後から分かる）。 */
    public static Consignee of(String name) {
        return new Consignee(name, null, null);
    }

    /**
     * 空文字を {@code null} に寄せる。
     *
     * <p>画面の未入力は空文字で届く。空文字のまま持つと「未確定」と
     * 「空という値が入力された」の区別がつかなくなる。
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
