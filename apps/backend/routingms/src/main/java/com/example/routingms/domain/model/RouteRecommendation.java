package com.example.routingms.domain.model;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 経路候補の推奨順と費用の概算（US08・[ADR-018]）。
 *
 * <p>経路設計者は一覧を上から見る。並びが業務の判断と合っていなければ、一覧そのものの
 * 意味が無い。並べ方をここに 1 つだけ置き、画面と API が別々に並べ替えないようにする。
 */
public final class RouteRecommendation {

    /** 区間 1 本あたりの基本輸送料金（概算）。 */
    private static final BigDecimal PER_LEG = new BigDecimal("200000");

    /** 輸送 1 日あたりの基本輸送料金（概算）。待つ日数も費用に効く。 */
    private static final BigDecimal PER_DAY = new BigDecimal("30000");

    /** 港 1 つあたりの港湾利用料（概算）。出発地・経由港・目的地のすべてで発生する。 */
    private static final BigDecimal PER_PORT = new BigDecimal("50000");

    private RouteRecommendation() {
    }

    /**
     * 推奨順に並べ替える。候補は増えも減りもしない。
     *
     * <p>順序は 3 段。
     *
     * <ol>
     *   <li><strong>直行便が最優先</strong>（US08 の受入基準）。遅く着いても上に出す。
     *       積み替えが無いことは、遅延も損傷も乗り継ぎ失敗も起きないという意味であり、
     *       経路設計者はまずそこを見る</li>
     *   <li>到着の早い順。荷主が待つ時間が短い</li>
     *   <li>積み替えの少ない順。荷役のたびに損傷と遅延の危険が上がる</li>
     * </ol>
     */
    public static List<TransitPath> rank(List<TransitPath> candidates) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .sorted(Comparator.comparing((TransitPath path) -> path.isDirect() ? 0 : 1)
                        .thenComparing(TransitPath::arrivalTime)
                        .thenComparing(TransitPath::transshipmentCount))
                .toList();
    }

    /**
     * 費用の概算（[UC06] の「基本輸送料金 + 港湾利用料の概算」）。
     *
     * <p><strong>請求される金額ではない。</strong>運賃表も港湾利用料のマスタも存在しない
     * （US21 / IT11 まで）。ここで出すのは経路どうしを見比べるための目安であり、
     * 画面にも概算であることを書く。US21 で実料金に差し替える。
     */
    public static BigDecimal estimatedCost(TransitPath path) {
        if (path == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal legs = PER_LEG.multiply(BigDecimal.valueOf(path.edges().size()));
        BigDecimal days = PER_DAY.multiply(BigDecimal.valueOf(path.transitDays()));
        // 出発地と目的地に、経由港を足した数だけ港を使う
        BigDecimal ports = PER_PORT.multiply(BigDecimal.valueOf((long) path.transitPorts().size() + 2));
        return legs.add(days).add(ports);
    }
}
