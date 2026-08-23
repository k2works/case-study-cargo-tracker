package com.example.handlingms.domain.model;

/**
 * 荷受人の確認（[ADR-023] 決定 4）。
 *
 * <p>引取のときに、荷役作業員が<strong>誰から確認を得たか</strong>を記録する。
 *
 * <p>これは通関ガード（{@code CustomsDeclaration}・US29・IT9）の<strong>代替</strong>である。
 * ガードが無いまま引取を通すと「通関前の貨物を引き渡した」記録が残る。空欄のまま通せる形に
 * しないために、確認を業務上の事実として残す。<strong>IT9 で本物のガードに置き換える。</strong>
 *
 * @param confirmedBy 確認を得た相手（荷受人の担当者名など）
 */
public record ConsigneeConfirmation(String confirmedBy) {

    public static ConsigneeConfirmation of(String confirmedBy) {
        if (confirmedBy == null || confirmedBy.isBlank()) {
            throw new IllegalArgumentException("荷受人の確認は必須です");
        }
        return new ConsigneeConfirmation(confirmedBy);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static ConsigneeConfirmation restore(String confirmedBy) {
        return confirmedBy == null ? null : new ConsigneeConfirmation(confirmedBy);
    }

    @Override
    public String toString() {
        return confirmedBy;
    }
}
