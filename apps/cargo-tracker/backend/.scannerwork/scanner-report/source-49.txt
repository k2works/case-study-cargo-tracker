package com.example.cargotracker.booking.application.port;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 連鎖の途中経過を扱う窓口。Reaction Handler はこれを通してだけ状態を触る。
 *
 * <p>Saga のインフラが隠していた関連付け・終了・タイムアウトを、ここで明示的に扱う。
 * 状態が自分のテーブルにあるので、滞留の一覧化も管理画面もふつうの SQL で書ける。</p>
 */
public interface ProcessStateService {

    /**
     * 連鎖を始める。
     *
     * <p>同じ連鎖が既にあれば作らずに現在の状態を返す。イベントは再配信されうるので、
     * 2 度目の呼び出しで最初からやり直すと、進んだ段が巻き戻る。</p>
     */
    ProcessState start(String processType, String processId, String firstStep, int totalSteps,
            Map<String, String> metadata);

    Optional<ProcessState> find(String processType, String processId);

    /**
     * 1 段進める。
     *
     * <p>同じ段を 2 度受け取っても進めない（イベントの再配信で段が飛ぶのを防ぐ）。
     * 進んだ結果すべての段を終えたら {@code COMPLETED} にする。</p>
     */
    ProcessState advance(String processType, String processId, String completedStep,
            String nextStep);

    /** 補償に至ったことを残す。理由は metadata に足す。 */
    ProcessState compensate(String processType, String processId, String reason);

    /** 滞留している連鎖（実行中のまま指定の時間より古いもの）。 */
    List<ProcessState> findStuck(String processType, Duration olderThan);
}
