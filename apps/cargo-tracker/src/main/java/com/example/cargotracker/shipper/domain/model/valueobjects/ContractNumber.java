package com.example.cargotracker.shipper.domain.model.valueobjects;

/**
 * 法人契約の契約番号（US03）。
 *
 * <p>精算のときに<strong>どの契約に基づく割引かを説明する</strong>ための番号である
 * （US22）。番号が無いと、割引の根拠を請求書に書けない。
 *
 * @param value 契約番号
 */
public record ContractNumber(String value) {

    public ContractNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("契約番号は必須です");
        }
        value = value.strip();
    }
}
