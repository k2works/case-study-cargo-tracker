package com.example.cargotracker.handling.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 訂正・取り消し申請の読み書き（US36）。 */
@Mapper
public interface CorrectionMapper {

    @Insert("""
            INSERT INTO handling_correction (
                handling_activity_id, request_type, reason,
                corrected_completion_time, corrected_note,
                requested_by, requested_at, status)
            VALUES (
                #{handlingActivityId}, #{requestType}, #{reason},
                #{correctedCompletionTime}, #{correctedNote},
                #{requestedBy}, #{requestedAt}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CorrectionRecord row);

    /**
     * 決定を反映する。<strong>WHERE の version が要である。</strong>
     *
     * <p>これを外すと、2 人の追跡管理者が同じ申請を同時に開いたとき、
     * <strong>後の決定が黙って前の決定を消す</strong>。
     */
    @Update("""
            UPDATE handling_correction
               SET status = #{status},
                   decided_by = #{decidedBy},
                   decided_at = #{decidedAt},
                   decision_reason = #{decisionReason},
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND version = #{version}
            """)
    int update(CorrectionRecord row);

    @Select("""
            SELECT id, handling_activity_id AS handlingActivityId,
                   request_type AS requestType, reason,
                   corrected_completion_time AS correctedCompletionTime,
                   corrected_note AS correctedNote,
                   requested_by AS requestedBy, requested_at AS requestedAt,
                   status, decided_by AS decidedBy, decided_at AS decidedAt,
                   decision_reason AS decisionReason, version
              FROM handling_correction
             WHERE id = #{id}
            """)
    CorrectionRecord findById(@Param("id") long id);

    /** 承認待ち。**古い順** — 待たせている申請から片づける。 */
    @Select("""
            SELECT id, handling_activity_id AS handlingActivityId,
                   request_type AS requestType, reason,
                   corrected_completion_time AS correctedCompletionTime,
                   corrected_note AS correctedNote,
                   requested_by AS requestedBy, requested_at AS requestedAt,
                   status, decided_by AS decidedBy, decided_at AS decidedAt,
                   decision_reason AS decisionReason, version
              FROM handling_correction
             WHERE status = 'PENDING'
             ORDER BY requested_at, id
            """)
    List<CorrectionRecord> findPending();

    /** 荷役に紐づく履歴。**却下も残す** — 却下したことも経緯である。 */
    @Select("""
            SELECT id, handling_activity_id AS handlingActivityId,
                   request_type AS requestType, reason,
                   corrected_completion_time AS correctedCompletionTime,
                   corrected_note AS correctedNote,
                   requested_by AS requestedBy, requested_at AS requestedAt,
                   status, decided_by AS decidedBy, decided_at AS decidedAt,
                   decision_reason AS decisionReason, version
              FROM handling_correction
             WHERE handling_activity_id = #{handlingActivityId}
             ORDER BY requested_at DESC, id DESC
            """)
    List<CorrectionRecord> findByHandlingActivityId(
            @Param("handlingActivityId") long handlingActivityId);

    @Select("SELECT COUNT(*) FROM handling_correction WHERE status = 'PENDING'")
    int countPending();
}
