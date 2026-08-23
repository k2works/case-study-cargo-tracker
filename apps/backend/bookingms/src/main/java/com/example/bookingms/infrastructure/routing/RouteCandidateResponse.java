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

        public RouteCandidateResponse {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        candidates = candidates == null ? null : List.copyOf(candidates);
        }


    public record Candidate(List<CandidateLeg> legs) {

        public Candidate {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        legs = legs == null ? null : List.copyOf(legs);
        }

    }

    public record CandidateLeg(
            String voyageNumber,
            String fromUnLocode,
            String toUnLocode,
            Instant departureTime,
            Instant arrivalTime) {
    }
}
