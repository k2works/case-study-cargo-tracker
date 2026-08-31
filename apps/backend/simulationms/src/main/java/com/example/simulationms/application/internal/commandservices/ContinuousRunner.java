package com.example.simulationms.application.internal.commandservices;

import com.example.simulationms.domain.model.valueobjects.ScenarioRequest;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;

/**
 * 継続実行が 1 件を走らせる出口。
 *
 * <p><strong>スケジューラは待たない。</strong>1 件は 14 工程で 10 秒前後かかるため、
 * 待つと間隔の設定が意味を失う。始めるだけ始めて、走っている数を問う。
 */
public interface ContinuousRunner {

    /** 1 件を始める。**戻るまで待たない**。 */
    void start(ScenarioRequest request, SessionId sessionId, Seed seed);

    /** いま走っている数。上限の判定に使う。 */
    int running();
}
