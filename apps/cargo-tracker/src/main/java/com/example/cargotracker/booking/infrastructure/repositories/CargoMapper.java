package com.example.cargotracker.booking.infrastructure.repositories;

import java.util.List;
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
                origin_unlocode, destination_unlocode, arrival_deadline,
                booking_status, routing_status,
                dimension_length, dimension_width, dimension_height, quantity, description,
                hazardous_class, un_number, proper_shipping_name,
                min_temperature, max_temperature, temperature_unit,
                version)
            VALUES (
                #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{shipperId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{cargoType}, #{weight},
                #{originUnlocode}, #{destinationUnlocode}, #{arrivalDeadline},
                #{bookingStatus}, #{routingStatus},
                #{dimensionLength}, #{dimensionWidth}, #{dimensionHeight},
                #{quantity}, #{description},
                #{hazardousClass}, #{unNumber}, #{properShippingName},
                #{minTemperature}, #{maxTemperature}, #{temperatureUnit},
                #{version})
            """)
    int insert(CargoRecord row);

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
    int updateStatus(CargoRecord row);

    /**
     * 経路の割り当てを反映する（US09 / US11）。楽観的ロック付き。
     *
     * <p><strong>予約状態は更新しない。</strong> 経路を確定しても
     * {@code booking_status} は動かない（遷移表 3）。ここで一緒に書くと、
     * 動かないはずの状態が動く余地を作る。
     */
    @Update("""
            UPDATE cargo
               SET routing_status = #{routingStatus},
                   misrouted_at = #{misroutedAt},
                   misrouted_location_unlocode = #{misroutedLocationUnlocode},
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
               AND version = #{version}
            """)
    int updateRouting(CargoRecord row);

    /** 旅程を入れ替えるため、既存の区間を削除する。 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM leg WHERE cargo_id = #{cargoId}")
    int deleteLegs(@Param("cargoId") long cargoId);

    /** 区間をまとめて登録する。**1 件ずつ INSERT しない。** */
    @Insert("""
            <script>
            INSERT INTO leg (
                cargo_id, voyage_number, load_location_unlocode,
                unload_location_unlocode, load_time, unload_time, seq_number)
            VALUES
            <foreach item="l" collection="legs" separator=",">
              (#{l.cargoId}, #{l.voyageNumber}, #{l.loadLocationUnlocode},
               #{l.unloadLocationUnlocode}, #{l.loadTime}, #{l.unloadTime}, #{l.seqNumber})
            </foreach>
            </script>
            """)
    int insertLegs(@Param("legs") List<LegRecord> legs);

    /**
     * 旅程の区間を順序どおりに取得する。
     *
     * <p><strong>ORDER BY seq_number を外さない。</strong> 順序が崩れると
     * 連結制約の検証で「つながっていない」と判定され、
     * <strong>保存できたものが読めなくなる</strong>。
     */
    @Select("""
            SELECT cargo_id, voyage_number, load_location_unlocode,
                   unload_location_unlocode, load_time, unload_time, seq_number
              FROM leg WHERE cargo_id = #{cargoId}
             ORDER BY seq_number
            """)
    List<LegRecord> findLegs(@Param("cargoId") long cargoId);

    /**
     * 追跡番号を発行する（US14）。楽観的ロック付き。
     *
     * <p><strong>予約状態と追跡番号を 1 つの UPDATE で書く。</strong> 分けると
     * 「追跡番号発行済なのに番号が無い」行を作れてしまう。
     */
    @Update("""
            UPDATE cargo
               SET booking_status = #{bookingStatus},
                   tracking_number = #{trackingNumber},
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
               AND version = #{version}
            """)
    int updateTrackingNumber(CargoRecord row);

    /**
     * 追跡番号から予約を引き当てる（US15 / US18）。
     *
     * <p>荷役作業員が手に持っているのは追跡番号だけである。
     */
    @Select("""
            SELECT id, booking_id, shipper_id, cargo_type, weight,
                   origin_unlocode, destination_unlocode, arrival_deadline,
                   booking_status, routing_status, tracking_number,
                   dimension_length, dimension_width, dimension_height, quantity, description,
                   hazardous_class AS hazardousClass, un_number AS unNumber,
                   proper_shipping_name AS properShippingName,
                   min_temperature AS minTemperature, max_temperature AS maxTemperature,
                   temperature_unit AS temperatureUnit,
                   consignee_name, consignee_address, consignee_email,
                   misrouted_at AS misroutedAt,
                   misrouted_location_unlocode AS misroutedLocationUnlocode,
                   version
              FROM cargo
             WHERE tracking_number = #{trackingNumber}
            """)
    CargoRecord findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    @Select("""
            SELECT id, booking_id, shipper_id, cargo_type, weight,
                   origin_unlocode, destination_unlocode, arrival_deadline,
                   booking_status, routing_status, tracking_number,
                   dimension_length, dimension_width, dimension_height, quantity, description,
                   hazardous_class AS hazardousClass, un_number AS unNumber,
                   proper_shipping_name AS properShippingName,
                   min_temperature AS minTemperature, max_temperature AS maxTemperature,
                   temperature_unit AS temperatureUnit,
                   consignee_name, consignee_address, consignee_email,
                   misrouted_at AS misroutedAt,
                   misrouted_location_unlocode AS misroutedLocationUnlocode,
                   version
              FROM cargo
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    CargoRecord findByBookingId(@Param("bookingId") UUID bookingId);

    /**
     * 荷受人を保存する（US16）。
     *
     * <p><strong>予約状態は動かさない。</strong> 荷受人の登録は状態遷移ではなく、
     * 予約に付随する情報の更新である（遷移表に対応する行が無い）。
     *
     * <p>楽観的ロックは付けない。<strong>荷受人の登録は「後から分かった情報を
     * 書き足す」操作</strong>であり、2 人が同時に登録しても後の内容が正しい。
     * 状態遷移と違い、先行した更新を消す危険が無い。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE cargo
               SET consignee_name    = #{consigneeName},
                   consignee_address = #{consigneeAddress},
                   consignee_email   = #{consigneeEmail},
                   updated_at        = CURRENT_TIMESTAMP
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    int updateConsignee(CargoRecord row);
}
