package com.example.cargotracker.handling.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 荷役作業の MyBatis マッパー。
 *
 * <p><strong>UUID の型ハンドラは明示する。</strong> 実行時は設定で解決されるが、
 * 設定を読まない道具（JIG）からは解決できず、このマッパーだけが読み飛ばされる
 * （IT5 の P5 で実測）。
 */
@Mapper
public interface HandlingMapper {

    @Insert("""
            INSERT INTO handling_activity (
                booking_id, event_type, event_completion_time,
                location_unlocode, voyage_number, tracking_number,
                claim_confirmation_method, claim_confirmation_code, claim_consignee_name,
                operator_name, version)
            VALUES (
                #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{eventType}, #{eventCompletionTime},
                #{locationUnlocode}, #{voyageNumber}, #{trackingNumber},
                #{claimConfirmationMethod}, #{claimConfirmationCode}, #{claimConsigneeName},
                #{operatorName}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HandlingActivityRecord row);

    /**
     * 予約ごとの荷役履歴を新しい順で返す。
     *
     * <p><strong>ORDER BY を外さない。</strong> 履歴は「最後に何が起きたか」を
     * 読むためのものであり、順序が崩れると現在地が読めなくなる。
     */
    @Select("""
            SELECT id, booking_id, event_type, event_completion_time,
                   location_unlocode, voyage_number, tracking_number,
                   claim_confirmation_method, claim_confirmation_code, claim_consignee_name,
                   operator_name, version
              FROM handling_activity
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
             ORDER BY event_completion_time DESC
            """)
    List<HandlingActivityRecord> findByBookingId(@Param("bookingId") UUID bookingId);

    /** 荷役履歴を新しい順で返す（荷役作業一覧）。 */
    @Select("""
            SELECT id, booking_id, event_type, event_completion_time,
                   location_unlocode, voyage_number, tracking_number,
                   claim_confirmation_method, claim_confirmation_code, claim_consignee_name,
                   operator_name, version
              FROM handling_activity
             ORDER BY event_completion_time DESC, id DESC
             LIMIT #{limit}
            """)
    List<HandlingActivityRecord> findRecent(@Param("limit") int limit);
}
