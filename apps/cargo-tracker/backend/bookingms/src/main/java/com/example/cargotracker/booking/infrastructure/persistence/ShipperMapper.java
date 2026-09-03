package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 荷主の投影テーブル。 */
@Mapper
public interface ShipperMapper {

    /** 荷主コードは投影側で採番する。集約で MAX+1 しない（data-model.md）。 */
    @Select("SELECT 'SHP-' || lpad(nextval('shipper_code_seq')::text, 6, '0')")
    String nextShipperCode();

    @Select("SELECT count(*) FROM shipper WHERE email = #{email}")
    int countByEmail(@Param("email") String email);

    /**
     * 重複相手の荷主 ID。要確認一覧が「既存の荷主を見る」の行き先に使う。
     *
     * <p>返すのは識別子だけ。メールアドレスは個人情報なので、応答に載せない。</p>
     */
    @Select("SELECT shipper_id FROM shipper WHERE email = #{email}")
    String findIdByEmail(@Param("email") String email);

    int insert(ShipperRow row);

    ShipperRow findById(@Param("shipperId") String shipperId);

    java.util.List<ShipperRow> findAll(@Param("limit") int limit, @Param("offset") int offset);

    /** 投影の行。個人情報の列は null になりうる（鍵破棄後）。 */
    record ShipperRow(
            String shipperId,
            String shipperCode,
            String shipperType,
            String name,
            String email,
            String phone,
            String address,
            String countryCode,
            String contractNumber,
            java.math.BigDecimal discountRate,
            Instant registeredAt,
            Instant projectedAt,
            String lastEventId) {
    }
}
