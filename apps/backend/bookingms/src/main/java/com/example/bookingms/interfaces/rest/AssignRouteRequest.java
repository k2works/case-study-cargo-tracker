package com.example.bookingms.interfaces.rest;

import java.util.List;

/**
 * 経路の割り当て（US09・[ADR-019] 決定 1）。
 *
 * <p><strong>候補の中身を丸ごと受け取る。</strong>候補 ID では参照しない（[ADR-017] が
 * 「候補は保存に見合わない」と決めたため、ID で引ける実体が無い）。
 *
 * <p><strong>地点は UN/LOCODE だけ受け取る。</strong>名称はサーバがマスタから引く。画面が
 * 送った名称を信じると、地点名の直しがマスタと予約の 2 か所に分かれる。
 *
 * @param maxTransshipments 候補を出したときに使った積み替えの上限（US10 で緩めた値）。
 *     成立の再検証を同じ条件で行うために受け取る
 */
public record AssignRouteRequest(List<LegRequest> legs, Integer maxTransshipments) {

    public record LegRequest(
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            String loadTime,
            String unloadTime) {
    }
}
