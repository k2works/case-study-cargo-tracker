package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 見積で示した 5 項目（正典の不変条件 1）。
 *
 * <p><b>比較をここ 1 か所に置く。</b> 集約（{@code Quotation#diffAgainst}）と
 * 予約の受付（{@code QuotationDiff}）の両方が使う——別々に書くと、片方だけが
 * 正しくてもう片方が違いを見落とす。IT10 で「判定をテスト側に書き直さない」と
 * 決めたのと同じ理由で、<b>本番の中でも書き直さない</b>。</p>
 *
 * <p><b>断るための型ではない。</b> 違いは項目名で知らせるだけで、予約そのものは
 * 通す（正典の不変条件 3）——荷主の事情は見積のあとで変わる。</p>
 */
public record QuotationTerms(
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg) {

    /**
     * 予約の入力から 5 項目を取り出す。
     *
     * <p><b>取り出し方も 1 か所に置く。</b> 呼ぶ側ごとに書くと、項目を足した
     * ときに片方だけが拾う。</p>
     */
    public static QuotationTerms of(
            com.example.cargotracker.booking.domain.model.commands.BookCargoCommand command) {
        var route = command.routeSpecification();
        var spec = command.cargoSpecification();
        return new QuotationTerms(
                route == null ? null : route.origin().unLocode().value(),
                route == null ? null : route.destination().unLocode().value(),
                route == null ? null : route.arrivalDeadline(),
                spec == null ? null : spec.cargoType().name(),
                spec == null ? null : spec.weight().kilograms());
    }

    /**
     * もう一方との違い。<b>「何から何へ」まで返す</b>——項目名だけでは、
     * 営業担当者は見積を開き直して見比べることになる。
     *
     * @param booked 予約の側の 5 項目
     * @return 違いの説明。<b>空なら見積どおり</b>
     */
    public List<String> differencesAgainst(QuotationTerms booked) {
        if (booked == null) {
            return List.of();
        }
        List<String> differences = new ArrayList<>();
        addIfDifferent(differences, "出発地", originUnLocode, booked.originUnLocode());
        addIfDifferent(differences, "目的地", destinationUnLocode, booked.destinationUnLocode());
        addIfDifferent(differences, "到着期限", arrivalDeadline, booked.arrivalDeadline());
        addIfDifferent(differences, "貨物種別", cargoType, booked.cargoType());

        // **数として比べる。** BigDecimal の equals は桁数まで見るので、
        // 1200 と 1200.00 が「違う」になる（見積どおりの予約が毎回違うと出る）。
        if (weightKg != null && booked.weightKg() != null
                && weightKg.compareTo(booked.weightKg()) != 0) {
            differences.add("重量: " + plain(weightKg) + " kg → "
                    + plain(booked.weightKg()) + " kg");
        }
        return List.copyOf(differences);
    }

    /**
     * 重量の見せ方を揃える。
     *
     * <p><b>比べ方だけ揃えても足りない。</b> 見積は列から {@code BigDecimal(2)} で
     * 戻るので、そのまま並べると「1200.00 kg → 1500 kg」になり、同じ単位の 2 つの
     * 数が別の書き方で出る。<b>桁は業務の意味を持たない</b>ので落とす。</p>
     */
    private static String plain(BigDecimal weight) {
        return weight.stripTrailingZeros().toPlainString();
    }

    private static void addIfDifferent(List<String> differences, String label,
            Object quoted, Object booked) {
        if (quoted != null && !quoted.equals(booked)) {
            differences.add(label + ": " + quoted + " → " + booked);
        }
    }
}
