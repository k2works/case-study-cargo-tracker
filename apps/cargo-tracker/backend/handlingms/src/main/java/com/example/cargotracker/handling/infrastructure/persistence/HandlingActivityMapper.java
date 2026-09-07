package com.example.cargotracker.handling.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 荷役の記録（US15）。正典は data-model.md「handling_activity」。 */
@Mapper
public interface HandlingActivityMapper {

    /** 読み出す列を並べる。<b>{@code SELECT *} にしない</b>（列順で割り当てられる）。 */
    String COLUMNS = "activity_id, tracking_number, booking_id, handling_type, unlocode, "
            + "voyage_number, consignee_name, off_route, operator, completed_at, "
            + "voided, voided_at, void_reason, projected_at";

    /**
     * 記録を 1 行足す（US15 §4）。
     *
     * <p><b>同じイベントを 2 度読んでも増えない。</b> 主キーは活動 ID で、
     * <b>クライアントが作る</b>ので通信断の再送でも同じ鍵になる。</p>
     */
    int insert(HandlingActivityRow row);

    /** 取り消しを記録する。<b>元の行は消さない</b>（不変条件 7）。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE handling_activity SET voided = TRUE, voided_at = #{voidedAt}, "
            + "void_reason = #{reason}, projected_at = #{projectedAt} "
            + "WHERE activity_id = #{activityId}")
    int markVoided(@Param("activityId") String activityId,
            @Param("reason") String reason,
            @Param("voidedAt") Instant voidedAt,
            @Param("projectedAt") Instant projectedAt);

    @Select("SELECT " + COLUMNS + " FROM handling_activity WHERE activity_id = #{activityId}")
    HandlingActivityRow findById(@Param("activityId") String activityId);

    /** その貨物の荷役履歴（S51）。<b>起きた順</b>に返す（記録した順ではない）。 */
    @Select("SELECT " + COLUMNS + " FROM handling_activity "
            + "WHERE tracking_number = #{trackingNumber} ORDER BY completed_at, activity_id")
    List<HandlingActivityRow> findHistory(@Param("trackingNumber") String trackingNumber);

    /**
     * この航海のこの港で送信済みの記録（S50）。<b>新しい順</b>。
     *
     * <p>荷役作業員は 1 隻から 20〜50 本を連続で記録する。直前に送ったものが
     * 上に積み上がると、取り違えに気づける。</p>
     */
    @Select("SELECT " + COLUMNS + " FROM handling_activity "
            + "WHERE voyage_number = #{voyageNumber} AND unlocode = #{unLocode} "
            + "ORDER BY completed_at DESC, activity_id DESC LIMIT #{limit}")
    List<HandlingActivityRow> findOnVoyage(@Param("voyageNumber") String voyageNumber,
            @Param("unLocode") String unLocode, @Param("limit") int limit);

    /** 記録の 1 行。 */
    record HandlingActivityRow(
            String activityId,
            String trackingNumber,
            String bookingId,
            String handlingType,
            String unlocode,
            String voyageNumber,
            String consigneeName,
            boolean offRoute,
            String operator,
            Instant completedAt,
            boolean voided,
            Instant voidedAt,
            String voidReason,
            Instant projectedAt) {
    }
}
