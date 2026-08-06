package com.example.cargotracker.booking.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 貨物予約の読み取り専用マッパー（CQRS のクエリ側）。
 *
 * <p>荷主名は JOIN で 1 回の SQL に含める。予約 1 件ごとに荷主を引き直すと
 * 一覧で N+1 になる。
 *
 * <p><strong>状態の表示名とキャンセル可否は SQL で組み立てない。</strong>
 * 遷移表の規則を SQL にも書くと、規則が 2 か所に散って必ず片方だけが更新される。
 * ここでは列挙子名までを返し、表示名と可否は {@code BookingStatus} から導く。
 */
@Mapper
public interface BookingQueryMapper {

    String SELECT_ROW = """
            SELECT CAST(c.booking_id AS VARCHAR) AS bookingId,
                   s.shipper_code                AS shipperCode,
                   s.name                        AS shipperName,
                   c.cargo_type                  AS cargoType,
                   c.weight                      AS weight,
                   c.origin_unlocode             AS origin,
                   c.destination_unlocode        AS destination,
                   c.arrival_deadline            AS arrivalDeadline,
                   c.booking_status              AS bookingStatus,
                   c.dimension_length            AS dimensionLength,
                   c.dimension_width             AS dimensionWidth,
                   c.dimension_height            AS dimensionHeight,
                   c.quantity                    AS quantity,
                   c.description                 AS description
              FROM cargo c
              JOIN shipper s ON s.id = c.shipper_id
            """;

    @Select("""
            <script>
            """ + SELECT_ROW + """
            <where>
              <if test="origin != null and origin != ''">
                AND c.origin_unlocode = #{origin}
              </if>
              <if test="destination != null and destination != ''">
                AND c.destination_unlocode = #{destination}
              </if>
              <if test="status != null and status != ''">
                AND c.booking_status = #{status}
              </if>
            </where>
            ORDER BY c.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BookingQueryRow> search(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 絞り込み後の総件数。ページ送りの総ページ数に使う。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM cargo c
            <where>
              <if test="origin != null and origin != ''">
                AND c.origin_unlocode = #{origin}
              </if>
              <if test="destination != null and destination != ''">
                AND c.destination_unlocode = #{destination}
              </if>
              <if test="status != null and status != ''">
                AND c.booking_status = #{status}
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("status") String status);

    /**
     * 経路割り当て待ち。**並び順は希望期限の昇順**（`ui_design.md`）。
     *
     * <p>期限が同じ予約の順序を安定させるため、予約 ID を第 2 キーに置く。
     * **一意なキーを最後に置かないと、ページ送りで同じ行が 2 度出たり消えたりする。**
     */
    @Select(SELECT_ROW + """
             WHERE c.booking_status = 'ROUTE_PROPOSED'
               AND c.routing_status = 'NOT_ROUTED'
             ORDER BY c.arrival_deadline, c.booking_id
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<BookingQueryRow> findAwaitingRouting(
            @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM cargo c
             WHERE c.booking_status = 'ROUTE_PROPOSED'
               AND c.routing_status = 'NOT_ROUTED'
            """)
    long countAwaitingRouting();

    @Select(SELECT_ROW + """
             WHERE c.booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    BookingQueryRow findByBookingId(@Param("bookingId") UUID bookingId);
}
