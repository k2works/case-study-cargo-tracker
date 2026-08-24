package com.example.bookingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CancellationRequestMapper {

    String COLUMNS = """
            id, cargo_id, reason, status, requested_by, requested_at,
            booking_status_at_request, discharge_location_unlocode,
            decided_by, decided_at, decision_reason
            """;

    @Insert("""
            INSERT INTO cancellation_request (
                cargo_id, reason, status, requested_by, requested_at,
                booking_status_at_request, discharge_location_unlocode,
                decided_by, decided_at, decision_reason)
            VALUES (
                #{cargoId}, #{reason}, #{status}, #{requestedBy}, #{requestedAt},
                #{bookingStatusAtRequest}, #{dischargeLocationUnlocode},
                #{decidedBy}, #{decidedAt}, #{decisionReason})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(CancellationRequestRecord row);

    /** **常に INSERT する save にしない。**承認・却下はこちらで書く。 */
    @Update("""
            UPDATE cancellation_request
               SET status = #{status},
                   discharge_location_unlocode = #{dischargeLocationUnlocode},
                   decided_by = #{decidedBy},
                   decided_at = #{decidedAt},
                   decision_reason = #{decisionReason},
                   updated_at = NOW()
             WHERE id = #{id}
            """)
    void updateDecision(CancellationRequestRecord row);

    @Select("SELECT " + COLUMNS + " FROM cancellation_request WHERE id = #{id}")
    @Results(id = "cancellationResult", value = {
        @Result(column = "cargo_id", property = "cargoId"),
        @Result(column = "requested_by", property = "requestedBy"),
        @Result(column = "requested_at", property = "requestedAt"),
        @Result(column = "booking_status_at_request", property = "bookingStatusAtRequest"),
        @Result(column = "discharge_location_unlocode", property = "dischargeLocationUnlocode"),
        @Result(column = "decided_by", property = "decidedBy"),
        @Result(column = "decided_at", property = "decidedAt"),
        @Result(column = "decision_reason", property = "decisionReason"),
    })
    CancellationRequestRecord findById(@Param("id") long id);

    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM cancellation_request
             WHERE cargo_id = #{cargoId}
               AND status = 'REQUESTED'
             ORDER BY requested_at DESC, id DESC
             LIMIT 1
            """)
    @ResultMap("cancellationResult")
    CancellationRequestRecord findAwaitingByCargoId(@Param("cargoId") long cargoId);

    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM cancellation_request
             WHERE cargo_id = #{cargoId}
             ORDER BY requested_at DESC, id DESC
             LIMIT 1
            """)
    @ResultMap("cancellationResult")
    CancellationRequestRecord findLatestByCargoId(@Param("cargoId") long cargoId);

    /** 承認待ちの一覧（US30-4）。**古い順**——放っておくほど貨物は目的地へ近づく。 */
    @Select("""
            SELECT
            """ + COLUMNS + """
              FROM cancellation_request
             WHERE status = 'REQUESTED'
             ORDER BY requested_at, id
             LIMIT #{limit}
            """)
    @ResultMap("cancellationResult")
    List<CancellationRequestRecord> findAwaitingDecision(@Param("limit") int limit);
}
