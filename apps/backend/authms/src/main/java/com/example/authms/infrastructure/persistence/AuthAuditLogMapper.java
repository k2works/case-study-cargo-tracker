package com.example.authms.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthAuditLogMapper {

    @Insert("""
            INSERT INTO auth_audit_log (username, event_type, detail)
            VALUES (#{username}, #{eventType}, #{detail})
            """)
    void insert(
            @Param("username") String username,
            @Param("eventType") String eventType,
            @Param("detail") String detail);
}
