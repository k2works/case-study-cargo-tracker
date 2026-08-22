package com.example.authms.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthAuditLogMapper {

    @Insert("""
            INSERT INTO auth_audit_log (username, event_type, detail, actor)
            VALUES (#{username}, #{eventType}, #{detail}, #{actor})
            """)
    void insert(
            @Param("username") String username,
            @Param("eventType") String eventType,
            @Param("detail") String detail,
            @Param("actor") String actor);
}
