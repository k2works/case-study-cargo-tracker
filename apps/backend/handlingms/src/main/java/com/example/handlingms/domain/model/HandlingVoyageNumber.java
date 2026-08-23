package com.example.handlingms.domain.model;

/**
 * 航海番号（Handling Context 固有の型）。
 *
 * <p>Routing Context の {@code VoyageNumber} とは別の型である。こちらは
 * <strong>どの船に載せたかの記録</strong>で、航海そのものを指すわけではない。
 *
 * @param value 航海番号の文字列
 */
public record HandlingVoyageNumber(String value) {

    public static HandlingVoyageNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        return new HandlingVoyageNumber(value);
    }

        /**
     * 永続化された行から復元する。<strong>列が空なら空を返す</strong>。
     *
     * <p>名前に {@code Nullable} を付けるのは、<strong>呼び出し側から null 可能性が
     * 見えないため</strong>である。{@code restore} という名前だけでは「復元できた何か」が
     * 返ると読める。ここでは検査しない。
     */
    public static HandlingVoyageNumber restoreNullable(String value) {
        return value == null ? null : new HandlingVoyageNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
