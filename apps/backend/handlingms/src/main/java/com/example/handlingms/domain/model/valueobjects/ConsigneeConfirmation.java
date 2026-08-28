package com.example.handlingms.domain.model.valueobjects;

/**
 * 荷受人の確認（[ADR-023] 決定 4）。
 *
 * <p>引取のときに、荷役作業員が<strong>誰から確認を得たか</strong>を記録する。
 *
 * <p><strong>これは US16 の受入基準そのものである。</strong>誰に引き渡したかの記録であり、
 * 通関ガードの代用として入れたのではない——IT7〜IT8 では代用も兼ねていたが、
 * IT9 で本物の通関ガード（{@code CustomsDeclaration}・[ADR-025] 決定 9）が入った
 * あとも<strong>この確認は残る</strong>。
 *
 * <p>通関ガードは引取の<strong>手前</strong>に立つ。順序が入れ替わると、確認を入れさえ
 * すれば通関前でも通ってしまう。
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

        /**
     * 永続化された行から復元する。<strong>列が空なら空を返す</strong>。
     *
     * <p>名前に {@code Nullable} を付けるのは、<strong>呼び出し側から null 可能性が
     * 見えないため</strong>である。{@code restore} という名前だけでは「復元できた何か」が
     * 返ると読める。ここでは検査しない。
     */
    public static ConsigneeConfirmation restoreNullable(String confirmedBy) {
        return confirmedBy == null ? null : new ConsigneeConfirmation(confirmedBy);
    }

    @Override
    public String toString() {
        return confirmedBy;
    }
}
