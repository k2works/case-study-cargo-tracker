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
        var entity = new UserSessionRecord();
        entity.setJti(session.jti());
        entity.setUserId(session.userId().value());
        entity.setIssuedAt(session.issuedAt());
        entity.setExpiresAt(session.expiresAt());
        entity.setRevoked(session.isRevoked());
        mapper.insert(entity);
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

    private UserSession toDomain(UserSessionRecord entity) {
        return UserSession.reconstruct(
                entity.getJti(),
                new UserId(entity.getUserId()),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.isRevoked());
    }
}
