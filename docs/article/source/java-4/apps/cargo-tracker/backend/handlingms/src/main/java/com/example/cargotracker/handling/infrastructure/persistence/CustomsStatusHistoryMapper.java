package com.example.cargotracker.handling.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 通関状態の変更履歴（US29 §受入基準 8）。正典は data-model.md。 */
@Mapper
public interface CustomsStatusHistoryMapper {

    /** 読み出す列を並べる。<b>{@code SELECT *} にしない</b>（列順で割り当てられる）。 */
    String COLUMNS = "event_id, declaration_number, kind, previous_status, status, reason, "
            + "changed_by, changed_at, projected_at";

    /**
     * 履歴を 1 行足す。
     *
     * <p><b>主キーは元イベントの識別子。</b> 追記の表なので、これが無いと読み直す
     * たびに積み上がる（IT6 の「追記専用の行はリプレイで増える」）。</p>
     */
    int insert(CustomsStatusHistoryRow row);

    /** その申告の履歴（S53）。<b>起きた順</b>に返す。 */
    @Select("SELECT " + COLUMNS + " FROM customs_status_history "
            + "WHERE declaration_number = #{declarationNumber} "
            + "ORDER BY changed_at, event_id")
    List<CustomsStatusHistoryRow> findHistory(
            @Param("declarationNumber") String declarationNumber);

    record CustomsStatusHistoryRow(
            String eventId,
            String declarationNumber,
            String kind,
            String previousStatus,
            String status,
            String reason,
            String changedBy,
            Instant changedAt,
            Instant projectedAt) {
    }
}
