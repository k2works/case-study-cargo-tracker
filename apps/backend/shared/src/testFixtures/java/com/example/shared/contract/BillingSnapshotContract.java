package com.example.shared.contract;

import java.util.List;

/**
 * 料金算出の入力を引く REST の契約（US21・[ADR-027] 決定 7）。
 *
 * <p><strong>{@link CargoSnapshotContract} と同じ形にする。</strong>終盤で新しい結合方式を
 * 発明しない（開発戦略）。違うのは、呼ぶ相手（billingms）と、運ぶ項目だけである。
 *
 * <p><strong>両側が同じ 1 つを読む。</strong>写しを 2 つ置くと、片方だけ直したことを誰も
 * 検出できない。
 *
 * <p>ここに置くのは<strong>契約であって実装ではない</strong>。DTO は BC ごとに持つ
 * （相手の型を持ち込まない）。共有するのは「両者が合意した経路と項目」だけである。
 */
public final class BillingSnapshotContract {

    private BillingSnapshotContract() {
    }

    /** 料金算出の対象になる予約を引く経路。{@code {bookingId}} は予約番号に置き換える。 */
    public static final String PATH = "/api/v1/bookings/{bookingId}/billing-snapshot";

    /**
     * 料金を算出していない引取済・キャンセル済みの予約を並べる経路。
     *
     * <p><strong>経理担当者は他に気づく手段を持たない</strong>（メールの仕組みは無い）。
     */
    public static final String UNBILLED_PATH = "/api/v1/bookings/billable";

    /**
     * 精算が済んだことを知らせる経路（US23-4・[ADR-028] 決定 1）。
     *
     * <p><strong>ここだけが副作用を持つ。</strong>ほかの 2 本は読み取りである。
     */
    public static final String SETTLEMENT_PATH = "/api/v1/bookings/{bookingId}/settlement";

    /**
     * 呼び出してよい主体（[ADR-019] 後日談 3）。
     *
     * <p>名乗らないと、相手の [ADR-007] フィルタが一律に断る。IT5 では名乗りを忘れ、
     * <strong>実環境の往復を通すまで誰も気づかなかった</strong>。
     */
    public static final String CALLER_PRINCIPAL = "system:billingms";

    /**
     * 流れる項目。順序も含めて契約である。
     *
     * <p><strong>誤配の記録を載せる</strong>（IT10 レビューの懸念）。予約詳細にしか出て
     * おらず、経理担当者はその画面を開けなかった——「残っている」と「読める」は別である。
     */
    public static final List<String> FIELDS =
            List.of("bookingId", "bookingStatus", "shipperId", "shipperName", "shipperType",
                    "discountRate", "weightKg", "cargoType", "originName",
                    "originCountry", "destinationName", "destinationCountry",
                    "legCount", "legs", "claimedAt", "misroute",
                    "cancellation");

    /**
     * 区間の項目（[ADR-027] 決定 1 の改訂）。
     *
     * <p><strong>地域区分を運ぶ。</strong>区間数だけでは、東京 → 横浜と
     * 東京 → ロサンゼルスが同額になる。<strong>係数は運ばない</strong>
     * ——料金の式は billingms の 1 か所にある（[ADR-028] 決定 6）。
     */
    public static final List<String> LEG_FIELDS =
            List.of("loadRegion", "unloadRegion");

    /** 誤配の記録の項目（US28-8）。誤配していなければ項目ごと現れない。 */
    public static final List<String> MISROUTE_FIELDS =
            List.of("at", "locationUnLocode", "locationName");

    /**
     * キャンセルの項目（US30-9）。
     *
     * <p><strong>申請した時点の状態を運ぶ</strong>——承認された時点ではない。料率は
     * 申請時の状態で決まる（正典のビジネスルール 6）。bookingms 側の列名は
     * {@code booking_status_at_request} であり、billing 側の
     * {@code booking_status_at_cancel} と<strong>同じ値に 2 つの名前がある</strong>
     * （[ADR-027] 注 16-a）。ここでは「申請時」の意味で運ぶ。
     */
    public static final List<String> CANCELLATION_FIELDS =
            List.of("bookingStatusAtRequest", "requestedAt");
}
