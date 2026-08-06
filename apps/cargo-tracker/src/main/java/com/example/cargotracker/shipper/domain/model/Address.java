package com.example.cargotracker.shipper.domain.model;

/**
 * 住所。
 *
 * <p><strong>番地以外は必須</strong>（US02 の受入基準・{@code data-model.md} の
 * {@code shipper} テーブル）。単一の文字列で持つと、国別の住所形式に対応できず
 * 検索も分解もできなくなる。
 *
 * @param country    国コード（ISO 3166-1 alpha-2）
 * @param postalCode 郵便番号
 * @param region     都道府県 / 州
 * @param city       市区町村
 * @param street     番地・建物名（任意）
 */
public record Address(
        String country, String postalCode, String region, String city, String street) {

    public Address {
        require(country, "国");
        if (country.length() != 2) {
            throw new IllegalArgumentException("国コードは ISO 3166-1 alpha-2（2 文字）です");
        }
        require(postalCode, "郵便番号");
        require(region, "都道府県");
        require(city, "市区町村");
    }

    private static void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "は必須です");
        }
    }
}
