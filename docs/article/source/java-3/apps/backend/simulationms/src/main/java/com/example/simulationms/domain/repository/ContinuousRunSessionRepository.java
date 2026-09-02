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

    /**
     * 直近のセッションを新しい順に返す（TD-03・IT16）。
     *
     * <p><strong>停止したセッションも残す。</strong>停止した瞬間に種が読めなくなると、
     * 翌朝には落ちた並びを再現する手立てが無い——US37-3 が言う「同じ種を指定すると
     * 同じ並びを再現できる」は、その種を読めて初めて意味を持つ。
     */
    java.util.List<ContinuousRunSession> findRecent(int limit);

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
