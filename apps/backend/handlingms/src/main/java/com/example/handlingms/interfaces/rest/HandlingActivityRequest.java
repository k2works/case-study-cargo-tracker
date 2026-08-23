package com.example.handlingms.interfaces.rest;

/**
 * 荷役作業を記録する要求（US15・US16）。
 *
 * <p><strong>{@code @Valid} は付けない</strong>（[ADR-016]）。付けるとメソッド本体に入る前に
 * 検証が走り、権限の無い呼び出しでも本文が不正なら 400 が返る。本人には「この操作はできない」
 * ではなく「入力を直せ」と伝わり、権限が無いはずの相手に入力仕様を教えることにもなる。
 *
 * <p><strong>日時は文字列で受ける。</strong>{@code Instant} で受け取ると、Spring は認可より
 * 先に変換を試み、失敗すると既定の 400 を返す（IT4 で実バックエンドのみ再現した形）。
 *
 * @param trackingNumber 追跡番号
 * @param type 荷役の種別
 * @param locationUnLocode 作業場所
 * @param completionTime 作業日時（ISO 8601）
 * @param operatorName 作業者
 * @param voyageNumber 航海番号。積込・荷降しでは必須
 * @param consigneeConfirmation 荷受人の確認。引取では必須
 */
public record HandlingActivityRequest(String trackingNumber, String type,
        String locationUnLocode, String completionTime, String operatorName,
        String voyageNumber, String consigneeConfirmation) {
}
