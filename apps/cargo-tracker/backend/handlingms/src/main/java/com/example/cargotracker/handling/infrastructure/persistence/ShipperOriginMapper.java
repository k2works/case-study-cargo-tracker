package com.example.cargotracker.handling.infrastructure.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 荷主がシミュレーション由来かどうかの写し（[ADR-0020] 決定 4）。
 *
 * <p><b>印だけを持つ。</b> 氏名も連絡先も写さない——荷役に要るのは
 * 「作業一覧から外すかどうか」だけである。</p>
 */
@Mapper
public interface ShipperOriginMapper {

    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO shipper_origin (shipper_id, simulated, projected_at) "
            + "VALUES (#{shipperId}, #{simulated}, #{projectedAt}) "
            + "ON CONFLICT (shipper_id) DO UPDATE SET "
            + "  simulated = EXCLUDED.simulated, projected_at = EXCLUDED.projected_at")
    int upsert(@Param("shipperId") String shipperId,
            @Param("simulated") boolean simulated,
            @Param("projectedAt") Instant projectedAt);

    @Select("SELECT simulated FROM shipper_origin WHERE shipper_id = #{shipperId}")
    Boolean findSimulated(@Param("shipperId") String shipperId);
}
