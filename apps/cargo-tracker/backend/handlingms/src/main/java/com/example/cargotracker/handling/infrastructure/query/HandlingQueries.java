package com.example.cargotracker.handling.infrastructure.query;

import java.time.Instant;
import java.util.List;

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
     * @param handledHere すでにこの港で記録済みか。連続記録で「残り」を数えるのに使う
     */
    public record CargoOnVoyageView(
            String trackingNumber,
            String bookingId,
            String originUnLocode,
            String destinationUnLocode,
            String cargoType,
            boolean handledHere) {
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
            boolean offRoute,
            String operator,
            Instant completedAt,
            boolean voided,
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
    public record FindCargoSnapshotQuery(String trackingNumber) {
    }

    /** S50 の「確認」欄に出す中身。 */
    public record CargoSnapshotView(
            String trackingNumber,
            String bookingId,
            String originUnLocode,
            String destinationUnLocode,
            String cargoType,
            List<LegView> legs) {
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

    /** 航海と港の一覧。 */
    public record VoyagePortListView(List<VoyagePortView> items) {
    }
}
