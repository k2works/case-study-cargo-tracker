package com.example.cargotracker.security.infrastructure.acl;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 荷主に紐付いた利用者名を読む（C11）。**読み取りだけである。** */
@Mapper
public interface LinkedAccountMapper {

    /** 利用者名を昇順で返す。**名前だけを返す** — 資格情報は境界を越えさせない。 */
    @Select("""
            SELECT username
              FROM users
             WHERE shipper_id = #{shipperId}
             ORDER BY username
            """)
    List<String> findUsernames(@Param("shipperId") UUID shipperId);
}
