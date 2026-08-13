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

/**
 * 経路提案の MyBatis マッパー。
 *
 * <p><strong>UUID の型ハンドラは明示する。</strong> 実行時は
 * {@code mybatis.type-handlers-package} の設定で解決されるが、
 * <strong>設定を読まない道具（Jig の CRUD 解析など）からは解決できず、
 * このマッパーだけが読み飛ばされる</strong>（実測）。
 * 他のマッパー（{@code CargoMapper}）と書き方も揃う。
 */
@Mapper
public interface RouteProposalMapper {

    @Insert("""
            INSERT INTO booking_route_proposal (
                booking_id, origin_unlocode, destination_unlocode,
                arrival_deadline, original_arrival_deadline,
                cargo_type, weight, max_transit_count,
                calculation_count, candidate_count, version)
            VALUES (
                #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}, #{originUnlocode}, #{destinationUnlocode},
                #{arrivalDeadline}, #{originalArrivalDeadline},
                #{cargoType}, #{weight}, #{maxTransitCount},
                #{calculationCount}, #{candidateCount}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RouteProposalRecord row);

    /**
     * 再算出の反映。楽観的ロック付き（判断 8）。
     *
     * <p>当初の期限（{@code original_arrival_deadline}）は<strong>更新しない</strong>。
     * 延ばした事実そのものが消えると、荷主に差分を伝えられない（US12）。
     *
     * <p><strong>WHERE 句の version が要である。</strong> これを外すと、2 人が同じ予約を
     * 同時に算出したとき、後の保存が黙って前の候補を消す。
     * <strong>列があるのに見ていなければ、持っていないのと同じ</strong>であり、
     * 次に読む人は守られていると誤解する。
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
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
               AND version = #{version}
            """)
    int update(RouteProposalRecord row);

    @Select("""
            SELECT p.id, p.booking_id, p.origin_unlocode, p.destination_unlocode,
                   p.arrival_deadline, p.original_arrival_deadline,
                   p.cargo_type, p.weight, p.max_transit_count,
                   p.calculation_count, p.candidate_count,
                   sel.voyage_number AS selectedVoyageNumber,
                   p.version
              FROM booking_route_proposal p
              LEFT JOIN proposed_route sel ON sel.id = p.selected_route_id
             WHERE p.booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
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
                boarding_index, landing_index,
                departure_date, arrival_date, transit_days,
                estimated_cost_value, estimated_cost_currency,
                capacity_available, hazardous_allowed, refrigerated_allowed,
                deadline_satisfied, priority)
            VALUES
            <foreach item="c" collection="candidates" separator=",">
              (#{c.proposalId}, #{c.voyageNumber}, #{c.transitPorts},
               #{c.boardingIndex}, #{c.landingIndex},
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
                   boarding_index, landing_index,
                   departure_date, arrival_date, transit_days,
                   estimated_cost_value, estimated_cost_currency,
                   capacity_available, hazardous_allowed, refrigerated_allowed,
                   deadline_satisfied, priority
              FROM proposed_route WHERE proposal_id = #{proposalId}
             ORDER BY priority
            """)
    List<ProposedRouteRecord> findCandidates(@Param("proposalId") long proposalId);

    /**
     * 選択を外す。
     *
     * <p><strong>候補を削除する前に必ず呼ぶ。</strong> {@code selected_route_id} は
     * {@code proposed_route} への外部キーであり、指したまま候補を消せない。
     */
    @Update("""
            UPDATE booking_route_proposal SET selected_route_id = NULL
             WHERE id = #{proposalId}
            """)
    int clearSelectedRoute(@Param("proposalId") long proposalId);

    /**
     * 選択した候補を記録する（US09）。
     *
     * <p>候補は再算出のたびに作り直されるため <strong>ID ではなく航海番号で引き当てる</strong>。
     */
    @Update("""
            UPDATE booking_route_proposal
               SET selected_route_id = (
                       SELECT id FROM proposed_route
                        WHERE proposal_id = #{proposalId}
                          AND voyage_number = #{voyageNumber}),
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{proposalId}
            """)
    int selectRoute(
            @Param("proposalId") long proposalId,
            @Param("voyageNumber") String voyageNumber);
}
