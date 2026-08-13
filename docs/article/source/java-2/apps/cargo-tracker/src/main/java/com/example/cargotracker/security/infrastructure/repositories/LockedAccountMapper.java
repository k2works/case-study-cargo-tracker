package com.example.cargotracker.security.infrastructure.repositories;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** ロック中アカウントの読み取り（US33）。 */
@Mapper
public interface LockedAccountMapper {

    /**
     * ロック中のアカウントを返す。
     *
     * <p><strong>「ロック期限が未来」だけを対象にする。</strong> 期限切れのものは
     * すでに解除されており、並べても解除する対象にならない。
     */
    @Select("""
            SELECT username, failed_attempts AS failedAttempts, locked_until AS lockedUntil
              FROM users
             WHERE locked_until IS NOT NULL
               AND locked_until > #{now}
             ORDER BY locked_until DESC
            """)
    List<LockedAccountRow> findLocked(@Param("now") Instant now);
}
