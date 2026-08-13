package com.example.cargotracker.security.infrastructure.repositories;

import com.example.cargotracker.security.application.internal.queryservices.LockedAccountQueryService;
import com.example.cargotracker.security.application.internal.queryservices.LockedAccountView;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link LockedAccountQueryService} の MyBatis 実装（読み取り専用アダプタ）。 */
@Service
public class MyBatisLockedAccountQueryService implements LockedAccountQueryService {

    private final LockedAccountMapper mapper;
    private final Clock clock;

    public MyBatisLockedAccountQueryService(LockedAccountMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public List<LockedAccountView> findLocked() {
        // 「今ロック中か」は時刻に依存する。**注入した時計で判断する**
        return mapper.findLocked(clock.instant()).stream()
                .map(row -> new LockedAccountView(
                        row.getUsername(), row.getFailedAttempts(), row.getLockedUntil()))
                .toList();
    }
}
