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

    /**
     * 追跡一覧（S40）。
     *
     * @param total 絞り込みに合う全件数。<b>上限で切れていることを黙らない</b>ため
     *     に返す——出ていない貨物は誰も追わない（「一覧に出ていない＝無い」と読む）
     */
    public record TrackingListView(List<TrackingListItemView> items, int total) {
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
            // その追跡の例外（US19 §受入基準 5）。**解決したものも出す**——
            // 事実は消えず、料金調整の根拠になる（不変条件 6）。
            List<TrackingExceptionView> exceptions,
            List<String> nextStatuses,
            // 誤配として扱っているか（US28 §受入基準 3）。**状態とは別に持つ**——
            // 例外の対応中は状態が例外発生へ退避するが、誤配であることは変わらない。
            // 画面はこれでバナーと `[経路を再設計]` の出し分けを決める。
            boolean misrouted) {
    }

    /** 追跡詳細に出す例外 1 件（S41）。 */
    public record TrackingExceptionView(
            String exceptionId,
            String exceptionType,
            String exceptionTypeLabel,
            String responseStatus,
            String responseStatusLabel,
            boolean urgent,
            String unLocode,
            String description,
            String resolution,
            // 対応で示した内容（US19 §4）。入力した値を画面まで返す。
            String newEstimatedArrival,
            String responsePlan,
            Instant occurredAt,
            Instant resolvedAt,
            // 荷主へ知らせた記録（US19 §3）。**送信基盤はスコープ外**なので、
            // これが読めることでしか受入基準を満たせない。
            List<ExceptionNotificationView> notifications) {
    }

    /** 荷主へ知らせた記録 1 件（S41）。 */
    public record ExceptionNotificationView(
            String means,
            String summary,
            String notifiedBy,
            Instant notifiedAt) {
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

    /**
     * 未解決の例外（S42 / UC16・US19 §受入基準 5）。
     *
     * <p><b>既定で解決済を外す。</b> 決着したものが混ざると、一覧全体が
     * 「まだ手を入れる場所」に見えなくなる。</p>
     */
    public record FindOpenExceptionsQuery(boolean includeResolved) {

        /** 既定は未解決だけ（S42 の既定）。 */
        public FindOpenExceptionsQuery() {
            this(false);
        }
    }

    /**
     * 例外一覧の 1 行（S42）。
     *
     * <p><b>緊急かどうかは載せる（種別の結果）。</b> 並びは緊急が先、以降は
     * 到着期限までの残日数が少ない順（不変条件 7）——読み口が並べたものを
     * 画面が並べ直さない。</p>
     */
    public record ExceptionView(
            String exceptionId,
            String trackingNumber,
            String exceptionType,
            String exceptionTypeLabel,
            String responseStatus,
            String responseStatusLabel,
            boolean urgent,
            String unLocode,
            String description,
            Instant occurredAt,
            // 対応で動いた期限を優先する（並びの根拠。US19 §4・不変条件 7）。
            java.time.LocalDate estimatedArrival,
            String transportStatus,
            String transportStatusLabel,
            // **電話は「A 社の予約の件で」から始まる**（IT10 レビュー N9）。
            // 荷主名は契約（TrackingInitializedEvent）に無いので、予約番号を出す。
            String bookingId,
            // **上位者へ知らせたか**（US20 §受入基準 3）。null なら未 escalation。
            // 「緊急なのに誰にも伝わっていない」を一覧で見分けられる。
            Instant escalatedAt) {
    }

    /** 例外一覧（S42）。 */
    public record ExceptionListView(List<ExceptionView> items) {
    }

    /**
     * 直近で状態が変わった追跡の件数（S02 荷主）。
     *
     * <p>US17 §受入基準 4 の「荷主への通知」は送信基盤がスコープ外。<b>荷主が
     * 自分で気づける手段</b>で代える（IT8 のレビュー指摘）。</p>
     */
    public record CountRecentlyChangedQuery(String shipperId, int withinHours) {
    }

    /** 件数と、その先の一覧への入口。 */
    public record RecentlyChangedView(int count, int withinHours) {
    }
}
