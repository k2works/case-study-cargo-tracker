package com.example.bookingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 荷主の輸送要件（受入基準 01-1）。
 *
 * <p><strong>5 つはいつも揃って動く。</strong>営業担当者は荷主から 5 つまとめて聞き、
 * 候補の探索にも料金の試算にも 5 つとも要る。ばらばらに渡すと、呼び出し側が
 * 「どれとどれが揃っていなければならないか」を知ることになる。
 *
 * <p><strong>予約との突き合わせもこの単位で行う</strong>（受入基準 01-7）。
 *
 * @param originUnLocode 出発地
 * @param destinationUnLocode 目的地
 * @param arrivalDeadline 希望期限
 * @param cargoType 貨物種別
 * @param weightKg 重量
 */
public record EstimateRequirements(String originUnLocode, String destinationUnLocode,
        LocalDate arrivalDeadline, CargoType cargoType, BigDecimal weightKg) {

    public EstimateRequirements {
        if (originUnLocode == null || originUnLocode.isBlank()
                || destinationUnLocode == null || destinationUnLocode.isBlank()) {
            throw new IllegalArgumentException("出発地と目的地を指定してください");
        }
        if (originUnLocode.equals(destinationUnLocode)) {
            // 同じ港へは運べない。予約（`RouteSpecification`）と同じ規則である
            throw new IllegalArgumentException("出発地と目的地が同じです: " + originUnLocode);
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("希望期限を指定してください");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別を指定してください");
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きい値で指定してください: " + weightKg);
        }
    }

    /**
     * 予約の内容と食い違っている項目（受入基準 01-7・US04 の未達）。
     *
     * <p><strong>断るためではなく、知らせるための材料である。</strong>条件が変わること
     * 自体は業務として普通に起きる（荷主が数量を増やす）。営業担当者が気づいて
     * 荷主に確かめられればよい。
     */
    public java.util.List<String> differencesFrom(EstimateRequirements booking) {
        if (booking == null) {
            return java.util.List.of();
        }
        java.util.List<String> differences = new java.util.ArrayList<>();
        if (!originUnLocode.equals(booking.originUnLocode())) {
            differences.add("出発地");
        }
        if (!destinationUnLocode.equals(booking.destinationUnLocode())) {
            differences.add("目的地");
        }
        if (!arrivalDeadline.equals(booking.arrivalDeadline())) {
            differences.add("到着期限");
        }
        if (cargoType != booking.cargoType()) {
            differences.add("貨物種別");
        }
        // **桁数ではなく値で比べる。** 4200 と 4200.000 は同じ重量である
        if (weightKg.compareTo(booking.weightKg()) != 0) {
            differences.add("重量");
        }
        return java.util.List.copyOf(differences);
    }
}
