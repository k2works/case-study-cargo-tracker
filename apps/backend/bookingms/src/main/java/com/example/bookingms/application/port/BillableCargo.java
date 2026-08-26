package com.example.bookingms.application.port;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 料金算出の入力（US21・[ADR-027] 決定 7）。
 *
 * <p><strong>読み取り専用の平たい形である</strong>（CQRS のクエリ側）。集約を経由せず、
 * 予約と荷主を JOIN した 1 行をそのまま運ぶ——請求のために貨物の状態を変えることはない。
 *
 * <p><strong>{@code application/port} に置く。</strong>出力ポートの戻り値であり、
 * {@code interfaces} に置くと application が interfaces に依存することになる
 * ——ArchUnit の「application は infrastructure と interfaces に依存しない」が捕まえた。
 * Controller はこれをそのまま返す。応答専用の型へ写し替えても項目は同じであり、
 * 増えるのは変換だけである。
 *
 * <p><strong>billingms の型は持ち込まない。</strong>こちらが返すのは bookingms 側の DTO で
 * あり、{@code Money} も {@code DiscountRate} も知らない。相手の言葉へ変換するのは
 * 受け取った側（ACL）の仕事である。
 *
 * @param bookingId 予約番号
 * @param bookingStatus 予約の状態（{@code DELIVERED} / {@code CANCELLED}）
 * @param shipperId 荷主 ID
 * @param shipperName 荷主の社名。<strong>画面に出す</strong>——経理担当者は社名で探す
 * @param shipperType 荷主種別（{@code CORPORATE} / {@code INDIVIDUAL}）
 * @param discountRate 契約割引率（0.0000〜0.3000 の<strong>率</strong>。百分率ではない
 *        ——{@code shipper.discount_rate} 列がそのまま率で持っている）。
 *        <strong>未設定なら {@code null}</strong>——0 ではない（[ADR-012]）
 * @param weightKg 重量
 * @param cargoType 貨物種別
 * @param originName 出発地の名前
 * @param destinationName 目的地の名前
 * @param legCount 旅程の区間数。<strong>距離の代わり</strong>（[ADR-027] 決定 1）
 * @param claimedAt 引取が完了した日時。キャンセルなら {@code null}
 * @param misroute 誤配の記録。無ければ {@code null}
 * @param cancellation キャンセルの記録。無ければ {@code null}
 */
public record BillableCargo(
        String bookingId,
        String bookingStatus,
        String shipperId,
        String shipperName,
        String shipperType,
        BigDecimal discountRate,
        BigDecimal weightKg,
        String cargoType,
        String originName,
        String destinationName,
        int legCount,
        Instant claimedAt,
        Misroute misroute,
        Cancellation cancellation) {

    /**
     * 誤配の記録（US28-8）。
     *
     * <p><strong>解決しても消えない</strong>（[ADR-026] 決定 3）。料金調整の根拠として
     * 参照される——IT10 はここまでで終わっており、読む相手がいなかった。
     *
     * @param at 予定ルート外の荷役が行われた日時
     * @param locationUnLocode その港
     * @param locationName その港の名前。地点マスタに無ければ {@code null}
     */
    public record Misroute(Instant at, String locationUnLocode, String locationName) {
    }

    /**
     * キャンセルの記録（US30-9）。
     *
     * <p><strong>申請した時点の状態を運ぶ。</strong>承認された時点ではない——料率は
     * 申請時の状態で決まる（正典のビジネスルール 6）。
     *
     * @param bookingStatusAtRequest 申請した時点の予約の状態
     * @param requestedAt 申請した日時
     */
    public record Cancellation(String bookingStatusAtRequest, Instant requestedAt) {
    }
}
