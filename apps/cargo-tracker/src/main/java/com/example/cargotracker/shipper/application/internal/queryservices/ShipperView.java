package com.example.cargotracker.shipper.application.internal.queryservices;

import java.math.BigDecimal;

/**
 * 荷主の画面表示用データ（CQRS のクエリ側）。
 *
 * <p>集約ではなく<strong>表示したい形</strong>である。ドメインモデルを画面に渡すと、
 * 画面の都合でドメインに getter が増え、集約の不変条件を守る意味が薄れる
 * （{@code architecture_frontend.md}「DTO の使用」）。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 16 個の要素が一列に並び、住所の 5 つ（国・郵便番号・都道府県・市区町村・番地）が
 * すべて {@code String} で隣り合っていた — <strong>順番を 1 つ間違えても
 * コンパイルは通り、画面に出て初めて分かる</strong>。
 *
 * <p><strong>画面が呼ぶ名前は委譲するアクセサで残している。</strong>
 *
 * @param id       荷主 ID（文字列）
 * @param code     荷主コード
 * @param type     荷主種別
 * @param contact  名称と連絡先
 * @param address  住所
 * @param contract 法人契約（個人荷主では空の値）
 * @param version  楽観的ロック用のバージョン
 */
public record ShipperView(
        String id,
        String code,
        Type type,
        Contact contact,
        Address address,
        Contract contract,
        long version) {

    /**
     * 荷主種別。
     *
     * @param name  列挙子名
     * @param label 表示名
     */
    public record Type(String name, String label) { }

    /**
     * 名称と連絡先。
     *
     * @param name  荷主名
     * @param email メールアドレス
     * @param phone 電話番号
     */
    public record Contact(String name, String email, String phone) { }

    /**
     * 住所。
     *
     * @param display    表示用に連結済みの住所
     * @param country    国コード
     * @param postalCode 郵便番号
     * @param region     都道府県 / 州
     * @param city       市区町村
     * @param street     番地
     */
    public record Address(
            String display,
            String country,
            String postalCode,
            String region,
            String city,
            String street) { }

    /**
     * 法人契約（US03）。
     *
     * @param number             契約番号。個人荷主では空文字
     * @param discountPercentage 契約割引率（百分率）。個人荷主では {@code null}
     */
    public record Contract(String number, BigDecimal discountPercentage) {

        /**
         * 法人契約を持つか。
         *
         * <p><strong>個人荷主の割引率は表示しない。</strong> {@code ui_design.md} は
         * 「個人は {@code -} を表示する。<strong>0% と {@code -} は意味が異なる</strong>
         * （個人には契約割引の概念自体が無い）」と定めている。
         */
        public boolean exists() {
            return number != null && !number.isBlank();
        }
    }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 荷主コード */
    public String shipperCode() {
        return code;
    }

    /** @return 荷主種別（列挙子名） */
    public String shipperType() {
        return type.name();
    }

    /** @return 荷主種別の表示名 */
    public String typeLabel() {
        return type.label();
    }

    /** @return 荷主名 */
    public String name() {
        return contact.name();
    }

    /** @return メールアドレス */
    public String email() {
        return contact.email();
    }

    /** @return 電話番号 */
    public String phone() {
        return contact.phone();
    }

    /** @return 国コード */
    public String addressCountry() {
        return address.country();
    }

    /** @return 郵便番号 */
    public String addressPostalCode() {
        return address.postalCode();
    }

    /** @return 都道府県 / 州 */
    public String addressRegion() {
        return address.region();
    }

    /** @return 市区町村 */
    public String addressCity() {
        return address.city();
    }

    /** @return 番地 */
    public String addressStreet() {
        return address.street();
    }

    /** @return 契約番号（個人荷主では空文字） */
    public String contractNumber() {
        return contract.number();
    }

    /** @return 契約割引率（百分率）。個人荷主では {@code null} */
    public BigDecimal discountRatePercentage() {
        return contract.discountPercentage();
    }

    /** 法人契約を持つか（US03）。 */
    public boolean hasContract() {
        return contract.exists();
    }
}
