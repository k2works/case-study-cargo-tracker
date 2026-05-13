package com.example.cargotracker.authms.infrastructure.persistence;

import com.example.cargotracker.authms.domain.model.UserId;
import com.example.cargotracker.authms.domain.model.UserSession;
import com.example.cargotracker.authms.domain.repository.UserSessionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisUserSessionRepository implements UserSessionRepository {

    private final UserSessionMapper mapper;

    public MyBatisUserSessionRepository(UserSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(UserSession session) {
        var record = new UserSessionRecord();
        record.setJti(session.jti());
        record.setUserId(session.userId().value());
        record.setIssuedAt(session.issuedAt());
        record.setExpiresAt(session.expiresAt());
        record.setRevoked(session.isRevoked());
        mapper.insert(record);
    }

    @Override
    public Optional<UserSession> findByJti(String jti) {
        return mapper.findByJti(jti).map(this::toDomain);
    }

    @Override
    public void revokeByJti(String jti) {
        mapper.revokeByJti(jti);
    }

    @Override
    public boolean isRevoked(String jti) {
        // 明示的に revoked = TRUE のレコードが存在する場合のみ無効化済みと判定する。
        // セッション不在（管理者ツールや起動時の直接トークン発行など）は "明示的にログアウトされていない" 扱い。
        // ログアウトはあくまでログイン経由で発行されたトークンに対する操作とする。
        return mapper.findByJti(jti)
                .map(UserSessionRecord::isRevoked)
                .orElse(false);
    }

    private UserSession toDomain(UserSessionRecord record) {
        return UserSession.reconstruct(
                record.getJti(),
                new UserId(record.getUserId()),
                record.getIssuedAt(),
                record.getExpiresAt(),
                record.isRevoked());
    }
}
