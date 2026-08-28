package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.valueobjects.HazardClass;

/**
 * 危険物クラスの選択肢。
 *
 * <p>コードだけを返すと画面が対訳表を持つことになり、分類名の直しが 2 箇所に分かれる。
 */
public record HazardClassResponse(String code, String label) {

    public static HazardClassResponse from(HazardClass hazardClass) {
        return new HazardClassResponse(hazardClass.code(), hazardClass.label());
    }
}
