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
