package com.example.billingms.application.port;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 料金算出の入力（[ADR-027] 決定 7）。
 *
 * <p><strong>bookingms の型ではない。</strong>ACL が相手の応答からこの形へ変換する
 * ——直接デシリアライズすると、bookingms のドメインの変更がこちらのコンパイルを壊す。
 *
 * <p>まだ {@code Money} でも {@code DiscountRate} でもない。<strong>境界を越えてきた
 * 生の値である</strong>——こちらの言葉へ移すのはユースケースの仕事である。
 *
 * @param bookingId 予約番号
 * @param bookingStatus 予約の状態
 * @param shipperId 荷主 ID
 * @param shipperName 荷主の社名
 * @param corporate 法人か
 * @param discountRate 契約割引率（率）。<strong>未設定なら {@code null}</strong>
 * @param weightKg 重量
 * @param cargoType 貨物種別
 * @param originName 出発地
 * @param originCountry 出発地の国コード（<strong>輸出免税の判定</strong>）
 * @param destinationName 目的地
 * @param destinationCountry 目的地の国コード
 * @param legCount 区間数
 * @param legs 旅程の区間（両端の地域区分）。<strong>距離の代わり</strong>
 * @param claimedAt 引取が完了した日時
 * @param misroute 誤配の記録。無ければ {@code null}
 * @param cancellation キャンセルの記録。無ければ {@code null}
 */
public record BillableCargoSnapshot(
        String bookingId,
        String bookingStatus,
        String shipperId,
        String shipperName,
        boolean corporate,
        BigDecimal discountRate,
        BigDecimal weightKg,
        String cargoType,
        String originName,
        String originCountry,
        String destinationName,
        String destinationCountry,
        int legCount,
        java.util.List<Leg> legs,
        Instant claimedAt,
        Misroute misroute,
        Cancellation cancellation) {

    /** 旅程の 1 区間。**区分は文字列で受ける**——こちらの言葉へ移すのはユースケース。 */
    public record Leg(String loadRegion, String unloadRegion) {
    }

    /** 誤配の記録（US28-8）。**料金調整の根拠**であり、金額そのものは決めない。 */
    public record Misroute(Instant at, String locationUnLocode, String locationName) {
    }

    /**
     * キャンセルの記録（US30-9）。
     *
     * <p><strong>申請した時点の状態を運ぶ</strong>——料率はそれで決まる。
     */
    public record Cancellation(String bookingStatusAtRequest, Instant requestedAt) {
    }
}
