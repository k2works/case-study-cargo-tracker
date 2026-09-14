package com.example.cargotracker.simulation.application;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.util.Map;

/**
 * 業務の API を叩く口（[ADR-0020] 決定 2）。
 *
 * <p><b>本物は Gateway 経由の HTTP を通る。</b> 呼ばれる側は本番の実装そのもので、
 * 自動化するのは呼び出し元だけである——専用の書き込み経路を作ると、
 * 「シミュレーションは通るのに実際の操作は通らない」状態を検出できなくなる。</p>
 *
 * <p><b>段取りと経路を同じ場所に書かない。</b> どの工程をどの順に実行するかは
 * シナリオが持ち、どの経路を叩くかはこの実装が持つ。</p>
 */
@FunctionalInterface
public interface BusinessApi {

    /**
     * 工程を 1 つ実行する。
     *
     * @param produced これまでの工程が生成した識別子。<b>前の工程の結果を
     *     次の工程が使う</b>——受け取りを捨てる実装にすると、つなぐ組み立てを
     *     潰しても緑のままになる
     */
    StepResult execute(StepKind kind, Map<StepKind, String> produced);

    /**
     * 工程の結果。
     *
     * @param producedId その工程が生成した識別子（無ければ {@code null}）
     * @param failureStatus 応答コード。<b>成功なら {@code null}</b>
     */
    record StepResult(String producedId, Integer failureStatus, String failureMessage) {

        /** 通った。 */
        public static StepResult success(String producedId) {
            return new StepResult(producedId, null, null);
        }

        /** 通らなかった。<b>理由を必ず持つ</b>——「失敗しました」では切り分けられない。 */
        public static StepResult failure(int status, String message) {
            return new StepResult(null, status, message);
        }

        /**
         * 応答コードの無い失敗。
         *
         * <p>相手が断ったのではなく、<b>自分が作ったものを読めなかった</b>ときに使う
         * （投影が追いつかない等）。ここを応答コードのある失敗に寄せると、
         * 「どちらが壊れているか」が読めなくなる。</p>
         */
        public static StepResult failure(String message) {
            return new StepResult(null, null, message);
        }

        /**
         * 通ったか。
         *
         * <p><b>理由の有無でも判定する。</b> 応答コードだけを見ると、コードの無い
         * 失敗が成功として通る——実際に IT16 でそう書いて、工程が止まっているのに
         * 「成功」と記録された。</p>
         */
        public boolean succeeded() {
            return failureStatus == null && failureMessage == null;
        }
    }
}
