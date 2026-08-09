package com.example.cargotracker.handling.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 通関申告の MyBatis マッパー（US29）。 */
@Mapper
public interface CustomsMapper {

    @Insert("""
            INSERT INTO customs_declaration (
                handling_activity_id, declaration_number, declared_at, status,
                cleared_at, held_since)
            VALUES (
                #{handlingActivityId}, #{declarationNumber}, #{declaredAt}, #{status},
                #{clearedAt}, #{heldSince})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CustomsDeclarationRecord row);

    @Update("""
            UPDATE customs_declaration
               SET status = #{status},
                   cleared_at = #{clearedAt},
                   held_since = #{heldSince},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
            """)
    int update(CustomsDeclarationRecord row);

    /** 変更履歴を積む。**理由はここが持つ**（申告本体だと上書きされる）。 */
    @Insert("""
            INSERT INTO customs_status_history (
                declaration_id, status_from, status_to, reason, changed_by, changed_at)
            VALUES (
                #{declarationId}, #{statusFrom}, #{statusTo}, #{reason},
                #{changedBy}, #{changedAt})
            """)
    int insertHistory(CustomsHistoryRecord row);

    @Select("""
            SELECT declaration_id AS declarationId, status_from AS statusFrom,
                   status_to AS statusTo, reason, changed_by AS changedBy,
                   changed_at AS changedAt
              FROM customs_status_history
             WHERE declaration_id = #{declarationId}
             ORDER BY changed_at, id
            """)
    List<CustomsHistoryRecord> findHistory(@Param("declarationId") long declarationId);

    @Select("""
            SELECT id, handling_activity_id AS handlingActivityId,
                   declaration_number AS declarationNumber, declared_at AS declaredAt,
                   status, cleared_at AS clearedAt, held_since AS heldSince
              FROM customs_declaration
             WHERE id = #{declarationId}
            """)
    CustomsDeclarationRecord findById(@Param("declarationId") long declarationId);

    /**
     * 追跡番号から申告を引く。
     *
     * <p><strong>荷役作業を経由する。</strong> 申告は「どの荷役でどの貨物を通したか」の
     * 記録であり、追跡番号を直接は持たない。
     */
    @Select("""
            SELECT d.id, d.handling_activity_id AS handlingActivityId,
                   d.declaration_number AS declarationNumber, d.declared_at AS declaredAt,
                   d.status, d.cleared_at AS clearedAt, d.held_since AS heldSince
              FROM customs_declaration d
              JOIN handling_activity h ON h.id = d.handling_activity_id
             WHERE h.tracking_number = #{trackingNumber}
             ORDER BY d.id DESC
             LIMIT 1
            """)
    CustomsDeclarationRecord findByTrackingNumber(
            @Param("trackingNumber") String trackingNumber);

    /**
     * 通関（CUSTOMS）の荷役作業 ID を引く。
     *
     * <p><strong>最新の 1 件を使う。</strong> 再通関で複数回記録されることがあり、
     * 申告は直近の通関作業に紐づける。
     */
    @Select("""
            SELECT id FROM handling_activity
             WHERE tracking_number = #{trackingNumber}
               AND event_type = 'CUSTOMS'
             ORDER BY event_completion_time DESC, id DESC
             LIMIT 1
            """)
    Long findCustomsHandlingId(@Param("trackingNumber") String trackingNumber);

    @Select("""
            SELECT h.tracking_number
              FROM customs_declaration d
              JOIN handling_activity h ON h.id = d.handling_activity_id
             WHERE d.id = #{declarationId}
            """)
    String findTrackingNumber(@Param("declarationId") long declarationId);
}
