package com.example.cargotracker.routing.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 経路提案の MyBatis マッパー。 */
@Mapper
public interface RouteProposalMapper {

    @Insert("""
            INSERT INTO booking_route_proposal (
                booking_id, origin_unlocode, destination_unlocode,
                arrival_deadline, original_arrival_deadline,
                cargo_type, weight, max_transit_count,
                calculation_count, candidate_count, version)
            VALUES (
                #{bookingId}, #{originUnlocode}, #{destinationUnlocode},
                #{arrivalDeadline}, #{originalArrivalDeadline},
                #{cargoType}, #{weight}, #{maxTransitCount},
                #{calculationCount}, #{candidateCount}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RouteProposalRecord row);

    /**
     * 再算出の反映。
     *
     * <p>当初の期限（{@code original_arrival_deadline}）は<strong>更新しない</strong>。
     * 延ばした事実そのものが消えると、荷主に差分を伝えられない（US12）。
     */
    @Update("""
            UPDATE booking_route_proposal
               SET origin_unlocode = #{originUnlocode},
                   destination_unlocode = #{destinationUnlocode},
                   arrival_deadline = #{arrivalDeadline},
                   cargo_type = #{cargoType},
                   weight = #{weight},
                   max_transit_count = #{maxTransitCount},
                   calculation_count = #{calculationCount},
                   candidate_count = #{candidateCount},
                   updated_at = CURRENT_TIMESTAMP
             WHERE booking_id = #{bookingId}
            """)
    int update(RouteProposalRecord row);

    @Select("""
            SELECT id, booking_id, origin_unlocode, destination_unlocode,
                   arrival_deadline, original_arrival_deadline,
                   cargo_type, weight, max_transit_count,
                   calculation_count, candidate_count, version
              FROM booking_route_proposal WHERE booking_id = #{bookingId}
            """)
    RouteProposalRecord findByBookingId(@Param("bookingId") UUID bookingId);

    /** 再算出のたびに候補を全削除する（ビジネスルール 5）。 */
    @Delete("DELETE FROM proposed_route WHERE proposal_id = #{proposalId}")
    int deleteCandidates(@Param("proposalId") long proposalId);

    /** 候補をまとめて登録する。**1 件ずつ INSERT しない。** */
    @Insert("""
            <script>
            INSERT INTO proposed_route (
                proposal_id, voyage_number, transit_ports,
                departure_date, arrival_date, transit_days,
                estimated_cost_value, estimated_cost_currency,
                capacity_available, hazardous_allowed, refrigerated_allowed,
                deadline_satisfied, priority)
            VALUES
            <foreach item="c" collection="candidates" separator=",">
              (#{c.proposalId}, #{c.voyageNumber}, #{c.transitPorts},
               #{c.departureDate}, #{c.arrivalDate}, #{c.transitDays},
               #{c.estimatedCostValue}, #{c.estimatedCostCurrency},
               #{c.capacityAvailable}, #{c.hazardousAllowed}, #{c.refrigeratedAllowed},
               #{c.deadlineSatisfied}, #{c.priority})
            </foreach>
            </script>
            """)
    int insertCandidates(@Param("candidates") List<ProposedRouteRecord> candidates);

    /**
     * 候補を表示順で取得する。
     *
     * <p><strong>ORDER BY priority を外さない。</strong> 順序が崩れると
     * 推奨順が意味を失い、経路設計者は「上から見る」ことができなくなる。
     */
    @Select("""
            SELECT proposal_id, voyage_number, transit_ports,
                   departure_date, arrival_date, transit_days,
                   estimated_cost_value, estimated_cost_currency,
                   capacity_available, hazardous_allowed, refrigerated_allowed,
                   deadline_satisfied, priority
              FROM proposed_route WHERE proposal_id = #{proposalId}
             ORDER BY priority
            """)
    List<ProposedRouteRecord> findCandidates(@Param("proposalId") long proposalId);
}
