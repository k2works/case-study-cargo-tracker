package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 要確認一覧（S70）。bookingms と同じ形。 */
@Mapper
public interface AttentionItemMapper {

    int insert(AttentionItemRow row);

    List<AttentionItemRow> findOpenByRole(@Param("assignedRole") String assignedRole);

    /**
     * 確認済にする（IT14 引き継ぎ A）。
     *
     * <p><b>担当宛であること・まだ確認されていないことを SQL の条件に置く。</b>
     * 呼び出し側で先に読んでから更新すると、そのあいだに他の人が確認した跡を
     * 上書きできてしまう。更新できた行数で判断する（0 なら対象が無い）。</p>
     */
    int acknowledge(@Param("itemId") String itemId,
            @Param("roles") List<String> roles,
            @Param("acknowledgedBy") String acknowledgedBy,
            @Param("acknowledgedAt") Instant acknowledgedAt);

    /** 要確認の行。 */
    record AttentionItemRow(
            String itemId,
            String kind,
            String targetType,
            String targetId,
            String assignedRole,
            String reason,
            String payload,
            Instant occurredAt,
            Instant acknowledgedAt,
            String acknowledgedBy) {
    }
}
