package com.example.simulationms.domain.repository;

import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import java.util.Optional;

/** 継続実行のセッション（US37）。 */
public interface ContinuousRunSessionRepository {

    /**
     * 状態を書き戻す。
     *
     * <p>実行の記録（{@code SimulationRun}）と違い、<strong>ここは更新する</strong>。
     * 停止の指示と、進行中が尽きたことは、どちらも状態そのものの変化である。
     */
    void save(ContinuousRunSession session);

    Optional<ContinuousRunSession> findById(SessionId sessionId);

    /**
     * いま動いているセッション（実行中または停止処理中）。
     *
     * <p><strong>停止済みは含めない。</strong>含めると、止めたはずのセッションが
     * また刻み始める。
     */
    Optional<ContinuousRunSession> findActive();

    /** その日に始まったセッションの数（セッション ID の連番に使う）。 */
    int countStartedOn(String prefix);
}
