package com.example.handlingms.infrastructure.booking;

import java.util.List;

/**
 * bookingms の応答を受ける、<strong>handlingms 側の</strong> DTO（[ADR-023] 決定 2）。
 *
 * <p>相手の型を直接デシリアライズすると、相手のドメインの変更がこちらのコンパイルを壊す。
 * ここで受けてから {@code CargoSnapshot} へ変換する。<strong>知らない項目は無視する</strong>
 * （相手が項目を足しても、こちらは壊れない）。
 *
 * @param bookingId 予約番号
 * @param originUnLocode 出発港
 * @param destinationUnLocode 目的港
 * @param legs 旅程の区間
 */
public record CargoSnapshotResponse(String bookingId, String originUnLocode,
        String destinationUnLocode, List<LegResponse> legs) {

    public CargoSnapshotResponse {
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    /**
     * 旅程の区間 1 本。
     *
     * @param voyageNumber 航海番号
     * @param loadUnLocode 積込港
     * @param unloadUnLocode 荷降港
     */
    public record LegResponse(String voyageNumber, String loadUnLocode, String unloadUnLocode) {
    }
}
