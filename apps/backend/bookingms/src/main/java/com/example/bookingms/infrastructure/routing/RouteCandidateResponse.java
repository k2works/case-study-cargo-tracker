package com.example.bookingms.infrastructure.routing;

import java.time.Instant;
import java.util.List;

/**
 * routingms の応答を受ける、<strong>bookingms 側の</strong> DTO（[ADR-019]）。
 *
 * <p>相手の型を直接デシリアライズすると、相手のドメインの変更がこちらのコンパイルを壊す。
 * ここで受けてから {@code CargoItinerary} へ変換する。<strong>知らない項目は無視する</strong>
 * （相手が項目を足しても、こちらは壊れない）。
 *
 * <p>受け取るのは旅程を組み立てるのに要る項目だけである。推奨順・費用の概算・待ち時間は
 * 画面が候補を見比べるための情報であり、予約に残す記録には含めない。
 */
public record RouteCandidateResponse(List<Candidate> candidates) {

    public record Candidate(List<CandidateLeg> legs) {
    }

    public record CandidateLeg(
            String voyageNumber,
            String fromUnLocode,
            String toUnLocode,
            Instant departureTime,
            Instant arrivalTime) {
    }
}
