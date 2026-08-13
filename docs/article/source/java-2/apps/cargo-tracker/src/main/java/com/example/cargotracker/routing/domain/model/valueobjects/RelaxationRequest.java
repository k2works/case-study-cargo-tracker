package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 探索条件を緩める要求（US10）。
 *
 * <p><strong>緩め方に上限を置く。</strong> 無制限に延ばせるなら、期限そのものが業務上の
 * 意味を失う。期限は荷主との約束であり、経路設計者が画面の操作だけで
 * 1 年先まで動かせてよいものではない。
 *
 * <p>上限を超えた要求は<strong>切り詰めず拒否する</strong>。黙って 30 日に丸めると、
 * 経路設計者は「400 日で探した結果、候補が無かった」と読む。
 * <strong>要求と違う条件で探した結果を、要求どおりの結果として返さない。</strong>
 *
 * @param extraDays       希望到着期限に上乗せする日数（0 なら期限は変えない）
 * @param maxTransitCount 経由回数の新しい上限（{@code null} なら変えない）
 */
public record RelaxationRequest(int extraDays, Integer maxTransitCount) {

    /**
     * 当初の希望期限から延ばせる日数の上限（<strong>累積</strong>）。
     *
     * <p>実務の期限調整は数日から数週間である。これを超える変更は
     * <strong>輸送計画そのものの引き直し</strong>であり、荷主との合意を経て
     * 予約を取り直す話になる。
     *
     * <p><strong>1 回ごとではなく合計で守る。</strong> 1 回ごとにしか見ないと、
     * 30 日を 14 回押して 420 日先まで動かせる。「画面の操作だけで到達してよい範囲では
     * ない」という判断は、合計で守らなければ意味を持たない。
     */
    public static final int MAX_EXTRA_DAYS = 30;

    /**
     * 経由回数の上限として指定できる最大値。
     *
     * <p><strong>探索の打ち切り条件である。</strong> 上限が無いと、港が増えるほど
     * 組み合わせが増える。5 回の乗り継ぎは実務上ほぼ上限であり、
     * それでも見つからないなら条件ではなく航路の側の問題である。
     */
    public static final int MAX_TRANSIT_COUNT_LIMIT = 5;

    public RelaxationRequest {
        if (extraDays < 0) {
            throw new IllegalArgumentException("期限を前倒しする再算出はできません");
        }
        if (extraDays > MAX_EXTRA_DAYS) {
            throw new IllegalArgumentException(
                    "延長できる日数の上限は当初の希望期限から合計 %d 日です: %d 日"
                            .formatted(MAX_EXTRA_DAYS, extraDays));
        }
        if (maxTransitCount != null) {
            if (maxTransitCount < 0) {
                throw new IllegalArgumentException("経由回数の上限は 0 以上です");
            }
            if (maxTransitCount > MAX_TRANSIT_COUNT_LIMIT) {
                throw new IllegalArgumentException(
                        "経由回数の上限は %d 回までです: %d 回"
                                .formatted(MAX_TRANSIT_COUNT_LIMIT, maxTransitCount));
            }
        }
    }

    /** 何も緩めない要求（US08 の初回算出はこれである）。 */
    public static RelaxationRequest none() {
        return new RelaxationRequest(0, null);
    }

    /** 何かを緩めているか。**緩めていない再算出は、条件の記録を書き換えない。** */
    public boolean relaxesAnything() {
        return extraDays > 0 || maxTransitCount != null;
    }

    /**
     * 条件に適用する。
     *
     * <p><strong>当初の期限は動かさない。</strong> 延ばした事実そのものが消えると、
     * 荷主に「何日延びたか」を伝えられない（US12）。
     */
    public RoutingCriteria applyTo(RoutingCriteria criteria) {
        RoutingCriteria relaxed = criteria;
        if (extraDays > 0) {
            // **合計で見る。** すでに延ばした分に積むため、1 回ごとの検査では足りない
            long total = java.time.temporal.ChronoUnit.DAYS.between(
                    criteria.originalArrivalDeadline(),
                    criteria.arrivalDeadline().plusDays(extraDays));
            if (total > MAX_EXTRA_DAYS) {
                throw new IllegalArgumentException(
                        "延長できる日数の上限は当初の希望期限から合計 %d 日です（今回で %d 日になります）"
                                .formatted(MAX_EXTRA_DAYS, total));
            }
            relaxed = relaxed.withDeadline(relaxed.arrivalDeadline().plusDays(extraDays));
        }
        if (maxTransitCount != null) {
            relaxed = relaxed.withMaxTransitCount(maxTransitCount);
        }
        return relaxed;
    }
}
