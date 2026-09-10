package com.example.cargotracker.handling.infrastructure.query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 荷役の読み取りモデル（domain-model.md「クエリ一覧」）。 */
public final class HandlingQueries {

    private HandlingQueries() {
    }

    /**
     * この航海がこの港で降ろす貨物（S50 の起点 / UC13）。
     *
     * <p><b>追跡番号は現場が持っていない。</b> 荷役作業員は船と港から始めて、
     * 貨物はスキャンで特定する。ここが引けないと画面が始まらない。</p>
     */
    public record FindCargosOnVoyageQuery(String voyageNumber, String unLocode) {
    }

    /**
     * S50 に出す貨物 1 件。
     *
     * @param handledTypes この港ですでに記録した種別。<b>種別で区別する</b>——引取が
     *     入ると同じ港で荷降し → 引取が起きるので、1 つの真偽値では
     *     「荷降しは済んだが引取はまだ」を表せない（M14）
     */
    public record CargoOnVoyageView(
            String trackingNumber,
            String bookingId,
            String originUnLocode,
            String destinationUnLocode,
            String cargoType,
            List<String> handledTypes) {
    }

    /** その港で引取を待っている貨物（H.8 / S02 荷役の下部タブ）。 */
    public record FindAwaitingClaimQuery(String unLocode) {
    }

    /** S50 の一覧。 */
    public record CargoOnVoyageListView(List<CargoOnVoyageView> items) {
    }

    /** 荷役履歴（S51 / UC13）。 */
    public record FindHandlingHistoryQuery(String trackingNumber) {
    }

    /** 荷役履歴の 1 行。 */
    public record HandlingHistoryItemView(
            String activityId,
            String handlingType,
            String handlingTypeLabel,
            String unLocode,
            String voyageNumber,
            // 荷受人の確認（引取のみ。US16）。取れていない種別では null。
            String consigneeName,
            boolean offRoute,
            String operator,
            Instant completedAt,
            boolean voided,
            Instant voidedAt,
            // 取り消した人（M13）。列が無かったころの行では null——画面は「—」と出す。
            String voidedBy,
            String voidReason) {
    }

    /** 荷役履歴。 */
    public record HandlingHistoryView(String trackingNumber, List<HandlingHistoryItemView> items) {
    }

    /**
     * 貨物 1 件の写し（S50 の「確認」欄 / スキャン後の照合）。
     *
     * <p>追跡番号を打ち込んだ直後に、予約番号・端点・貨物種別を出して
     * 「その貨物で合っているか」を確かめる。</p>
     */
    public record FindCargoSnapshotQuery(String trackingNumber, String unLocode) {
    }

    /** S50 の「確認」欄に出す中身。 */
    public record CargoSnapshotView(
            String trackingNumber,
            String bookingId,
            String originUnLocode,
            String destinationUnLocode,
            String cargoType,
            List<LegView> legs,
            // 種別ごとに、その港での作業が予定外か（H.5）。港を渡さなければ null。
            // **判定は CargoSnapshot#isOffRoute が答える。** 画面に書き直させない。
            Map<String, Boolean> offRouteByType) {
    }

    /** 予定の旅程の 1 区間。<b>時刻は持たない</b>（[ADR-0012] 決定 4）。 */
    public record LegView(String voyageNumber, String loadUnLocode, String unloadUnLocode) {
    }

    /**
     * これから作業する航海と港（S02 荷役のダッシュボード）。
     *
     * <p><b>件数だけでは仕事が進まない。</b> どの航海のどの港を開けばよいかまで出す
     * （IT4 の「気づく手段は次の行動へ繋ぐ」）。</p>
     */
    public record FindVoyagePortsQuery() { // NOSONAR: 型が問い合わせの識別子
    }

    /** 航海と港の組 1 件。 */
    public record VoyagePortView(String voyageNumber, String unLocode, int cargoCount) {
    }

    /**
     * 航海と港の一覧（S02 荷役）。
     *
     * <p><b>上限で切れたことを黙らない</b>（IT10 レビュー N6）。無音で切ると、
     * 載らなかった航海は誰の目にも入らないまま残る——荷役の現場は毎朝ここから
     * 入るので、載っていない航海は「今日の仕事ではない」と読まれる。</p>
     */
    public record VoyagePortListView(List<VoyagePortView> items, boolean truncated) {
    }

    /**
     * 通関申告の一覧（S52 / US29 §受入基準 7）。
     *
     * <p><b>既定で通関済を外す。</b> 決着したものが混ざると、一覧全体が「まだ手を
     * 入れる場所」に見えなくなる。</p>
     */
    public record FindCustomsDeclarationsQuery(
            boolean includeCleared, String trackingNumber, String status, boolean overdueOnly) {
    }

    /** 通関申告 1 件（S53 / 引取のガードも読む）。 */
    public record FindCustomsDeclarationQuery(String declarationNumber) {
    }

    /** その貨物の最新の通関状態（引取のガードが読む。US29 §受入基準 3）。 */
    public record FindCustomsStatusOfCargoQuery(String trackingNumber) {
    }

    /**
     * 状態の変更履歴（S53 / US29 §受入基準 8）。
     *
     * <p><b>{@code customs_status_history} 投影から読む。</b> 正典は当初
     * 「Event Store から読む」だったが、この版では `@QueryHandler` から
     * イベント列を読むと 0 件になる（IT12 で実測）。主キーは元イベントの
     * 識別子なので、リプレイで行が積み上がらない（[ADR-0012] と同じ形）。</p>
     */
    public record FindCustomsHistoryQuery(String declarationNumber) {
    }

    /** 督促の対象（留置が既定の営業日数を超えた申告）の件数（S02 / §受入基準 6）。 */
    public record CountOverdueCustomsHoldsQuery() { // NOSONAR: 型が問い合わせの識別子
    }

    /**
     * S52 に出す申告 1 件。
     *
     * @param heldBusinessDays 留置してからの営業日数。<b>読むときに数える</b>——
     *     留置中は日が経つだけで変わり、イベントは来ないので列は古くなる
     * @param overdue 督促の対象か。<b>判定はサーバが持つ</b>（画面で数え直さない）
     */
    public record CustomsDeclarationView(
            String declarationNumber,
            String trackingNumber,
            String bookingId,
            String status,
            String statusLabel,
            Instant declaredAt,
            Instant lastStatusChangedAt,
            Instant lastHeldAt,
            int heldBusinessDays,
            boolean overdue,
            String lastReason,
            String changedBy) {
    }

    public record CustomsDeclarationListView(List<CustomsDeclarationView> items, int total) {
    }

    /**
     * 履歴の 1 行（S53）。
     *
     * @param kind 何が起きたか。{@code STATUS_CHANGED} / {@code CLEARANCE_NOTIFIED}
     */
    public record CustomsHistoryEntryView(
            String kind,
            String previousStatus,
            String status,
            String statusLabel,
            String reason,
            String changedBy,
            Instant changedAt) {
    }

    public record CustomsHistoryView(List<CustomsHistoryEntryView> items) {
    }
}
