package com.example.simulationms.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * 継続実行の上限（US37-2・[ADR-031] 決定 3）。
 *
 * <p>同時に走る実行の数と、実行の間隔と、例外を起こす割合を持つ。
 *
 * <p><strong>上限の上限を置く。</strong>置かないと、設定 1 つで業務を止められる——
 * 負荷をかける側を自分で作る以上、自分でクラスタを落とさないことが要る。
 * IT7 で、横断的な防御を一律に適用した結果、過負荷でヘルスチェックが弾かれて
 * 再起動ループになった。
 *
 * @param intervalSeconds 次の実行を始めるまでの間隔（秒）
 * @param maxConcurrent 同時に走らせる実行の数
 * @param exceptionRatio 例外シナリオを選ぶ割合（0〜1）
 */
public record ContinuousRunPolicy(int intervalSeconds, int maxConcurrent,
        BigDecimal exceptionRatio) {

    /** 同時実行数として設定できる上限。 */
    public static final int MAX_CONCURRENT_LIMIT = 20;

    public ContinuousRunPolicy {
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException(
                    "実行の間隔は 1 秒以上で指定します: " + intervalSeconds);
        }
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException(
                    "同時実行数は 1 以上で指定します: " + maxConcurrent);
        }
        if (maxConcurrent > MAX_CONCURRENT_LIMIT) {
            throw new IllegalArgumentException(
                    "同時実行数は " + MAX_CONCURRENT_LIMIT + " 以下で指定します: " + maxConcurrent);
        }
        if (exceptionRatio == null
                || exceptionRatio.compareTo(BigDecimal.ZERO) < 0
                || exceptionRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "例外の割合は 0 から 1 の間で指定します: " + exceptionRatio);
        }
    }

    public static ContinuousRunPolicy of(int intervalSeconds, int maxConcurrent,
            BigDecimal exceptionRatio) {
        return new ContinuousRunPolicy(intervalSeconds, maxConcurrent, exceptionRatio);
    }

    /**
     * いま新しい実行を始めてよいか。
     *
     * <p><strong>判定はここ 1 つに置く。</strong>呼ぶ側で書き直すと、上限を変えたときに
     * 書き直した側だけが古いまま残る。
     */
    public boolean allows(int running) {
        return running < maxConcurrent;
    }
}
