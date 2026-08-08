package com.example.cargotracker.shipper.application.internal.queryservices;

/**
 * 荷主の画面表示用データ（CQRS のクエリ側）。
 *
 * <p>集約ではなく<strong>表示したい形</strong>である。ドメインモデルを画面に渡すと、
 * 画面の都合でドメインに getter が増え、集約の不変条件を守る意味が薄れる
 * （{@code architecture_frontend.md}「DTO の使用」）。
 *
 * @param id          荷主 ID（文字列）
 * @param shipperCode 荷主コード
 * @param shipperType 荷主種別（列挙子名）
 * @param typeLabel   荷主種別の表示名
 * @param name        荷主名
 * @param email       メールアドレス
 * @param phone       電話番号
 * @param address     住所（表示用に連結済み）
 * @param addressCountry    国コード
 * @param addressPostalCode 郵便番号
 * @param addressRegion     都道府県 / 州
 * @param addressCity       市区町村
 * @param addressStreet     番地
 * @param contractNumber 契約番号。個人荷主では空文字（US03）
 * @param discountRatePercentage 契約割引率（百分率）。個人荷主では {@code null}
 * @param version     楽観的ロック用のバージョン
 */
public record ShipperView(
        String id,
        String shipperCode,
        String shipperType,
        String typeLabel,
        String name,
        String email,
        String phone,
        String address,
        String addressCountry,
        String addressPostalCode,
        String addressRegion,
        String addressCity,
        String addressStreet,
        String contractNumber,
        java.math.BigDecimal discountRatePercentage,
        long version) {

    /**
     * 法人契約を持つか（US03）。
     *
     * <p><strong>個人荷主の割引率は表示しない。</strong> {@code ui_design.md} は
     * 「個人は {@code -} を表示する。<strong>0% と {@code -} は意味が異なる</strong>
     * （個人には契約割引の概念自体が無い）」と定めている。
     */
    public boolean hasContract() {
        return contractNumber != null && !contractNumber.isBlank();
    }
}
