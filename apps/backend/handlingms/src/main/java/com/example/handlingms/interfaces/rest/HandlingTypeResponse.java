package com.example.handlingms.interfaces.rest;

import com.example.handlingms.domain.model.HandlingType;

/**
 * 荷役の種別と、その要件（[ADR-023] 決定 1）。
 *
 * <p><strong>要件はサーバが答える。</strong>画面が「積込なら航海番号が要る」と書くと、
 * 規則が種別と画面の 2 か所に分かれ、片方だけ直る形になる（IT7 返済枠 0.7 と同じ形）。
 *
 * @param type 種別の名前
 * @param label 画面に出す名前
 * @param requiresVoyageNumber 航海番号が必須か
 * @param requiresConsigneeConfirmation 荷受人の確認が必須か
 */
public record HandlingTypeResponse(String type, String label, boolean requiresVoyageNumber,
        boolean requiresConsigneeConfirmation) {

    public static HandlingTypeResponse from(HandlingType type) {
        return new HandlingTypeResponse(type.name(), labelOf(type), type.requiresVoyageNumber(),
                type.requiresConsigneeConfirmation());
    }

    /**
     * 画面に出す名前。
     *
     * <p><strong>対訳表を画面に置かない。</strong>置くと、種別を足したときに直す場所が
     * 増える（[ADR-013] と同じ理由）。
     */
    private static String labelOf(HandlingType type) {
        return switch (type) {
            case RECEIVE -> "受領";
            case LOAD -> "積込";
            case UNLOAD -> "荷降し";
            case CLAIM -> "引取";
        };
    }
}
