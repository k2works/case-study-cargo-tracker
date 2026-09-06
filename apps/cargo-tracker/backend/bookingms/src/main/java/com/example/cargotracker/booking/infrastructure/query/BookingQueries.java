package com.example.cargotracker.booking.infrastructure.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 貨物予約の読み取りモデル（domain-model.md「クエリ一覧」）。 */
public final class BookingQueries {

    private BookingQueries() {
    }

    public record FindBookingQuery(String bookingId) {
    }

    /**
     * 一覧（S20）。
     *
     * <p>{@code includeFinished} は「終了したものも表示」の操作に対応する。既定を
     * false にしているのは、精算済とキャンセルが混ざると一覧全体が「今日やること」
     * として信用されなくなるため（ui_design.md「一覧の既定条件」）。</p>
     */
    public record FindBookingsQuery(int page, int size, boolean includeFinished) {
    }

    /**
     * 状態ごとの件数（S02 の「今日の作業」）。
     *
     * <p>「仮受付の件数」に限定しない。誤配の件数（S30）も同じ形で数えるので、
     * 状態を引数に取る。専用のクエリを状態の数だけ足すと、増やすたびに配線が増える。</p>
     */
    public record CountBookingsByStatusQuery(String bookingStatus) {
    }

    /**
     * 経路設計作業一覧（S30）。
     *
     * <p>{@code includeRouted} は「設計済みも表示」の操作に対応する。既定を false に
     * しているのは、設計の済んだ予約が混ざると一覧全体が「今日やること」として
     * 信用されなくなるため。誤配は既定でも含める（現在地からの再設計が要る）。</p>
     */
    public record FindRoutingWorklistQuery(int page, int size, boolean includeRouted) {
    }

    /**
     * 修正履歴（S22 / US32 §受入基準 4）。
     *
     * <p>一覧（{@code FindBookingsQuery}）には載せない。全件ぶんの履歴を読むことに
     * なるうえ、一覧では読まない。</p>
     */
    public record FindBookingRevisionsQuery(String bookingId) {
    }

    /** 1 回の修正で変わった項目 1 つ。新しい修正が先に並ぶ。 */
    public record RevisionView(
            Instant updatedAt,
            String updatedBy,
            String label,
            String before,
            String after) {
    }

    public record RevisionListView(List<RevisionView> items) {
    }

    /**
     * 確定した旅程（S22 / US09）。
     *
     * <p>一覧には載せない。全件ぶんの区間を読むことになるうえ、一覧では読まない。</p>
     */
    public record FindBookingItineraryQuery(String bookingId) {
    }

    /** 旅程の区間 1 つ。<b>並び順が業務の意味を持つ。</b> */
    public record ItineraryLegView(
            int legSeq,
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            Instant loadAt,
            Instant unloadAt) {
    }

    public record ItineraryView(List<ItineraryLegView> legs) {
    }

    /**
     * その航海で経路を組んだ予約（S34 / US24）。
     *
     * <p>航海を止める前に、巻き込む予約を数え、そこへ行けるようにする。止めても
     * 予約側の旅程は自動では戻らないので、<b>誰が影響を受けるかを止める前に知る</b>
     * 必要がある。マニュアル 07 章は「控えてください」と書いているが、控える先が
     * 無かった（IT5 引き継ぎ 2）。</p>
     *
     * <p>読むのは {@code cargo_leg}。{@code voyage_number} の索引が既にある。</p>
     */
    public record FindBookingsByVoyageQuery(String voyageNumber) {
    }

    /**
     * 巻き込む予約 1 件。
     *
     * <p><b>一覧の {@code BookingView} を使い回さない。</b> 止める判断に要るのは
     * 「どの予約が」「いまどの状態か」だけで、荷主名や貨物の寸法まで運ぶと、
     * 航海を読む人に予約の中身を余分に見せることになる。</p>
     */
    public record AffectedBookingView(
            String bookingId,
            String bookingNumber,
            String bookingStatus,
            String routingStatus) {
    }

    /** 巻き込む予約の一覧。件数は {@code items().size()} で足りる。 */
    public record AffectedBookingListView(List<AffectedBookingView> items) {
    }

    /**
     * 見直しを頼まれている予約（S02 / 営業。US10 §受入基準 4）。
     *
     * <p>件数だけでは仕事が進まないので、行そのものを返して予約詳細へ行けるように
     * する（IT4 の「気づく手段は次の行動へ繋ぐ」）。</p>
     */
    public record FindConditionReviewsQuery(int limit) {
    }

    /** 見直しを頼まれている予約 1 件。理由が読めないと、営業は何を協議するか分からない。 */
    public record ConditionReviewView(
            String bookingId,
            String bookingNumber,
            String reason,
            Instant requestedAt) {
    }

    public record ConditionReviewListView(List<ConditionReviewView> items) {
    }

    /** 調整された探索条件（US10）。候補を出すたびに投影から組む。 */
    public record FindRouteConditionQuery(String bookingId) {
    }

    /**
     * 探索の条件。<b>画面にも出す</b>（S31 の「いまの条件」）。
     *
     * <p>いま何で絞っているのかが読めないと、経路設計者は同じ条件で何度も再算出する。</p>
     */
    public record RouteConditionView(
            List<String> excludeUnLocodes,
            String departFromUnLocode) {
    }

    /**
     * 荷主へ通知していない経路確定済みの予約の件数（S02 / 営業。US12）。
     *
     * <p>履歴テーブルを数えず {@code last_notified_at} で絞る。</p>
     *
     * <p><b>引数を持たない。</b> Axon のクエリは<b>型そのものが問い合わせの識別子</b>で、
     * この問い合わせに絞り込みの余地は無い（「経路が決まっていて、まだ通知していない」
     * は 1 つの状態である）。数えるだけの引数を足すと、呼ぶ側が「別の状態でも数え
     * られる」と読んでしまう。静的解析の「空のクラス」指摘はこの形に当たらない。</p>
     */
    public record CountAwaitingNotificationQuery() { // NOSONAR: 型が問い合わせの識別子
    }

    /** 通知履歴（S22 / US12 §受入基準 4）。新しい通知が先。 */
    public record FindBookingNotificationsQuery(String bookingId) {
    }

    /**
     * 通知 1 件。<b>何を伝えたかを残す。</b>
     *
     * <p>要約が読めないと、荷主から「聞いていない」と言われたときに突き合わせられない。</p>
     */
    public record NotificationView(
            Instant notifiedAt,
            String recipientEmail,
            String summary,
            String notifiedBy) {
    }

    public record NotificationListView(List<NotificationView> items) {
    }

    /** 画面に出す予約。荷主名は鍵破棄後に {@code null} になる。 */
    public record BookingView(
            String bookingId,
            String bookingNumber,
            String shipperId,
            String shipperName,
            String originUnLocode,
            String destinationUnLocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            int quantity,
            String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC,
            String bookingStatus,
            String routingStatus,
            Instant bookedAt,
            // 経路設計者へ引き渡した日時（US06）。引き渡していなければ null。
            // 期限が遠い案件が S30 の下に沈んで放置されたことに気づく手立て。
            Instant routingRequestedAt,
            // 最後に荷主へ通知した日時（US12）。一度も通知していなければ null。
            // 画面は「通知履歴を問い合わせるか」をこの値で決める。
            Instant lastNotifiedAt,
            // 営業が経路設計へ戻した日時と理由（US12）。**経路設計者が読む。**
            // 記録だけ残して読み口を出さないと、営業に無駄な入力をさせることになる。
            Instant returnedToRoutingAt,
            String returnReason,
            // 調整済みの探索条件（US10）。**候補算出の応答から切り離して持つ。**
            // 探索が落ちていても、条件の調整と差し戻しは使えなければならない
            // （IT6 引き継ぎ 8b）。調整していなければ空リストと null。
            List<String> routeExcludeUnLocodes,
            String routeDepartFromUnLocode,
            // 最終更新（US32）。変更内容の履歴は Event Store が持つ。
            Instant updatedAt,
            String updatedBy) {
    }

    public record BookingListView(List<BookingView> items, int total) {
    }
}
