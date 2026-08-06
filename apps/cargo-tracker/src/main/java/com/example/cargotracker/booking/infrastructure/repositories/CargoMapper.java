package com.example.cargotracker.booking.infrastructure.repositories;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 貨物予約の MyBatis マッパー。
 *
 * <p>UUID のパラメータには TypeHandler を明示する（{@code ShipperMapper} と同じ理由）。
 * {@code mybatis.type-handlers-package} の設定を読まない解析ツール（JIG）で
 * SQL の抽出に失敗し、CRUD 図からマッパーが丸ごと欠落するのを防ぐ。
 */
@Mapper
public interface CargoMapper {

    String UUID_HANDLER =
            "typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler";

    @Insert("""
            INSERT INTO cargo (
                booking_id, shipper_id, cargo_type, weight,
                origin_unlocode, destination_unlocode, arrival_deadline, booking_status,
                dimension_length, dimension_width, dimension_height, quantity, description,
                version)
            VALUES (
                #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{shipperId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{cargoType}, #{weight},
                #{originUnlocode}, #{destinationUnlocode}, #{arrivalDeadline}, #{bookingStatus},
                #{dimensionLength}, #{dimensionWidth}, #{dimensionHeight},
                #{quantity}, #{description},
                #{version})
            """)
    int insert(CargoRecord record);

    /**
     * 楽観的ロック付きの更新。
     *
     * <p><strong>WHERE 句の version が要である。</strong> これを外すと、2 人が同じ予約を
     * 同時に編集したとき後の更新が黙って前の更新を消す。更新件数 0 が
     * 「他の更新が先行した」ことを表す。
     */
    @Update("""
            UPDATE cargo
               SET booking_status = #{bookingStatus},
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
               AND version = #{version}
            """)
    int updateStatus(CargoRecord record);

    @Select("""
            SELECT booking_id, shipper_id, cargo_type, weight,
                   origin_unlocode, destination_unlocode, arrival_deadline, booking_status,
                   dimension_length, dimension_width, dimension_height, quantity, description,
                   version
              FROM cargo
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    CargoRecord findByBookingId(@Param("bookingId") UUID bookingId);
}
