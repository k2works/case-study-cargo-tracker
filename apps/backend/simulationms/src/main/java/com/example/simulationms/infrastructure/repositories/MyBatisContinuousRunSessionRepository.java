package com.example.simulationms.infrastructure.repositories;

import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.SessionStatus;
import com.example.simulationms.domain.repository.ContinuousRunSessionRepository;
import java.util.Optional;

/** 継続実行のセッションを MyBatis で永続化する。 */
public class MyBatisContinuousRunSessionRepository implements ContinuousRunSessionRepository {

    private final ContinuousRunSessionMapper mapper;

    public MyBatisContinuousRunSessionRepository(ContinuousRunSessionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 作成と更新を分ける。
     *
     * <p><strong>常に INSERT する save は、更新で行を増やす。</strong>作成しか
     * 起きないうちは表面化せず、最初の停止で壊れる（IT7 の教訓）。
     */
    @Override
    public void save(ContinuousRunSession session) {
        ContinuousRunSessionRecord row = toRecord(session);
        if (mapper.findBySessionId(session.sessionId().value()) == null) {
            mapper.insert(row);
        } else {
            mapper.updateStatus(row);
        }
    }

    @Override
    public Optional<ContinuousRunSession> findById(SessionId sessionId) {
        return Optional.ofNullable(mapper.findBySessionId(sessionId.value())).map(this::toDomain);
    }

    @Override
    public Optional<ContinuousRunSession> findActive() {
        return Optional.ofNullable(mapper.findActive()).map(this::toDomain);
    }

    @Override
    public int countStartedOn(String prefix) {
        return mapper.countStartedOn(prefix);
    }

    private static ContinuousRunSessionRecord toRecord(ContinuousRunSession session) {
        ContinuousRunSessionRecord row = new ContinuousRunSessionRecord();
        row.setSessionId(session.sessionId().value());
        row.setSeed(session.seed().value());
        row.setIntervalSeconds(session.policy().intervalSeconds());
        row.setMaxConcurrent(session.policy().maxConcurrent());
        row.setExceptionRatio(session.policy().exceptionRatio());
        row.setStatus(session.status().name());
        row.setStartedBy(session.startedBy());
        row.setStartedAt(session.startedAt());
        row.setStoppedAt(session.stoppedAt().orElse(null));
        return row;
    }

    @Override
    public java.util.List<ContinuousRunSession> findRecent(int limit) {
        return mapper.findRecent(limit).stream().map(this::toDomain).toList();
    }

    private ContinuousRunSession toDomain(ContinuousRunSessionRecord row) {
        return ContinuousRunSession.restore(SessionId.of(row.getSessionId()),
                Seed.of(row.getSeed()),
                ContinuousRunPolicy.of(row.getIntervalSeconds(), row.getMaxConcurrent(),
                        row.getExceptionRatio()),
                SessionStatus.valueOf(row.getStatus()), row.getStartedBy(),
                row.getStartedAt(), row.getStoppedAt());
    }
}
