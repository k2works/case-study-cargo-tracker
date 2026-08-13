package com.example.cargotracker.handling.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 通関申告一覧の読み取り（US29。CQRS のクエリ側）。
 *
 * <p><strong>荷主名を JOIN する。</strong> 一覧は「連絡が要る仕事の待ち行列」であり、
 * 誰の貨物かが読めないと 1 件ずつ予約を開くことになる。
 * <strong>BC をまたぐ直接参照ではない</strong> — 読み取り側の SQL である
 * （{@code MyBatisHandlingQueryService} が貨物種別を JOIN するのと同じ理由）。
 */
@Mapper
public interface CustomsListMapper {

    /**
     * 申告を検索する。
     *
     * <p><strong>並び順は「留置を先に、申告の新しい順」。</strong> 片づいたものが
     * 上に来ると、いま何をすべきかが読めない（例外一覧と同じ判断）。
     */
    @Select("""
            <script>
            SELECT d.id, d.declaration_number AS declarationNumber,
                   h.tracking_number AS trackingNumber,
                   CAST(c.booking_id AS VARCHAR) AS bookingId,
                   d.status, d.declared_at AS declaredAt, d.cleared_at AS clearedAt,
                   d.held_since AS heldSince, s.name AS shipperName
              FROM customs_declaration d
              JOIN handling_activity h ON h.id = d.handling_activity_id
              JOIN cargo c ON c.booking_id = h.booking_id
              JOIN shipper s ON s.id = c.shipper_id
            <where>
              <if test="status != null">AND d.status = #{status}</if>
              <if test="keyword != null">
                AND (h.tracking_number LIKE '%' || #{keyword} || '%'
                  OR d.declaration_number LIKE '%' || #{keyword} || '%'
                  OR CAST(c.booking_id AS VARCHAR) LIKE '%' || #{keyword} || '%')
              </if>
            </where>
             ORDER BY CASE WHEN d.status = 'HELD' THEN 0 ELSE 1 END,
                      d.declared_at DESC, d.id DESC
            </script>
            """)
    List<CustomsListRow> search(
            @Param("keyword") String keyword, @Param("status") String status);

    @Select("""
            SELECT d.id, d.declaration_number AS declarationNumber,
                   h.tracking_number AS trackingNumber,
                   CAST(c.booking_id AS VARCHAR) AS bookingId,
                   d.status, d.declared_at AS declaredAt, d.cleared_at AS clearedAt,
                   d.held_since AS heldSince, s.name AS shipperName
              FROM customs_declaration d
              JOIN handling_activity h ON h.id = d.handling_activity_id
              JOIN cargo c ON c.booking_id = h.booking_id
              JOIN shipper s ON s.id = c.shipper_id
             WHERE d.id = #{declarationId}
            """)
    CustomsListRow findById(@Param("declarationId") long declarationId);
}
