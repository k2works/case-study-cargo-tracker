package com.example.cargotracker.tracking.infrastructure.query;

import java.time.Instant;
import java.util.List;

/** 追跡の読み取りモデル（domain-model.md「クエリ一覧」）。 */
public final class TrackingQueries {

    private TrackingQueries() {
    }

    /**
     * 公開照会（S44 / US18）。<b>認証を要らない</b>。
     *
     * <p>荷主は予約のたびに口座を作らず、渡された番号だけで見る。だからこそ
     * 番号は推測しにくい形式で採る（[ADR-0011]）。</p>
     */
    public record FindPublicTrackingQuery(String trackingNumber) {
    }

    /**
     * 公開照会で見せる中身（S44）。
     *
     * <p><b>公開画面には例外の詳細・荷主名・金額を出さない</b>（ui_design.md）。
     * ここに載せなければ、画面が間違えても漏れない。</p>
     *
     * @param statusLabel 利用者に見せる呼び名。<b>列挙名を渡さない</b>——{@code NOT_RECEIVED}
     *     と出ると業務担当者に意味が分からず、マニュアルとも照合できない
     * @param estimatedArrival 到着予定。<b>予定の旅程の最終区間の荷降し</b>（1 か所で決める）
     * @param history 起きた順の履歴
     */
    public record PublicTrackingView(
            String trackingNumber,
            String originUnLocode,
            String destinationUnLocode,
            String statusLabel,
            String currentUnLocode,
            Instant departedAt,
            Instant estimatedArrival,
            List<PublicTrackingEventView> history) {
    }

    /** 公開照会の履歴 1 行（S44）。 */
    public record PublicTrackingEventView(
            Instant occurredAt,
            String statusLabel,
            String location) {
    }
}
