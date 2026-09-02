package com.example.handlingms.domain.model.valueobjects;

/**
 * 旅程の区間 1 本の写し（[ADR-023] 決定 2）。
 *
 * <p>Booking Context の {@code Leg} とは別の型である。こちらが要るのは
 * <strong>作業場所を照らすための港</strong>だけで、日時も地点名も持たない。
 * 相手の型をそのまま持ち込むと、向こうの項目が増えるたびにこちらが影響を受ける。
 *
 * @param voyageNumber 航海番号
 * @param loadUnLocode 積込港
 * @param unloadUnLocode 荷降港
 */
public record LegSnapshot(String voyageNumber, String loadUnLocode, String unloadUnLocode) {
}
