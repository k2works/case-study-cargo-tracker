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

    /**
     * 追跡一覧（S40 / UC15）。<b>追跡管理者と荷主の両方が見る</b>。
     *
     * <p>{@code shipperId} が入っていれば自社のぶんだけを返す（US18）。<b>絞り込みは
     * ここで受けて SQL で行う</b>——読んでから捨てると、絞り忘れが情報漏れになる。</p>
     *
     * <p>{@code includeDelivered} は「引取済も表示」の操作に対応する。既定を false に
     * しているのは、引き取られた貨物が混ざると一覧全体が「いま追うもの」として
     * 信用されなくなるため（ui_design.md「一覧の既定条件」）。</p>
     */
    public record FindTrackingsQuery(String shipperId, boolean includeDelivered, int limit) {
    }

    /** 追跡一覧の 1 行（S40）。 */
    public record TrackingListItemView(
            String trackingNumber,
            String originUnLocode,
            String destinationUnLocode,
            String statusLabel,
            String currentUnLocode,
            Instant estimatedArrival,
            Instant lastStatusChangedAt) {
    }

    /** 追跡一覧（S40）。 */
    public record TrackingListView(List<TrackingListItemView> items) {
    }

    /**
     * 追跡詳細（S41 / UC14・UC15）。
     *
     * <p>公開照会（{@link FindPublicTrackingQuery}）と<b>別のクエリにする</b>。
     * こちらは認証の内側で、予約 ID のような業務の識別子を返す。1 つのクエリに
     * まとめて「公開のときだけ隠す」形にすると、隠し忘れが漏れになる。</p>
     *
     * <p>{@code shipperId} が入っていれば、その荷主のものでなければ返さない（US18）。</p>
     */
    public record FindTrackingQuery(String trackingNumber, String shipperId) {
    }

    /** 追跡詳細（S41）。 */
    public record TrackingView(
            String trackingNumber,
            String bookingId,
            String originUnLocode,
            String destinationUnLocode,
            String cargoType,
            String status,
            String statusLabel,
            String currentUnLocode,
            Instant estimatedArrival,
            Instant lastStatusChangedAt,
            List<TrackingEventView> history,
            List<String> nextStatuses) {
    }

    /**
     * 追跡詳細の履歴 1 行（S41）。
     *
     * <p>公開照会と違い<b>誰が動かしたか</b>も出す。社内の画面なので、手動更新の
     * 責任者が読めないと後から突き合わせられない。</p>
     */
    public record TrackingEventView(
            Instant occurredAt,
            String eventType,
            String previousStatusLabel,
            String statusLabel,
            String location,
            String recordedBy) {
    }
}
