package com.example.cargotracker.simulation.application;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.util.Map;

/**
 * 連鎖の結果が読めるようになったか（US33 §受入基準 6）。
 *
 * <p><b>待ち時間で判別しない。</b> 本システムは 7 サービスに分かれ、工程の多くは
 * 契約イベントの連鎖で進む。連鎖は結果整合なので、投影が追いつく前に次を叩くと
 * <b>通っている経路が失敗として記録される</b>（IT11・IT15 のクラスタ E2E で実測）。
 * かといって固定の秒数を眠ると、速い日は無駄に遅く、混んだ日は足りない。</p>
 *
 * <p><b>どの工程で何を待つかは宣言で決まる。</b> 実行が推測すると、工程を足した
 * ときに待ちの有無が暗黙になる。</p>
 */
@FunctionalInterface
public interface ChainReadiness {

    /**
     * 次へ進める状態か。<b>読み口が返した値</b>で判別する。
     *
     * @param produced これまでの工程が生成した識別子。読み口を引く鍵になる
     */
    boolean isReady(StepKind kind, Map<StepKind, String> produced);
}
