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

/** 見積の永続化（US01）。 */
@Mapper
public interface EstimateMapper {

    String COLUMNS = """
            id, estimate_id, estimate_number, origin_unlocode, destination_unlocode,
            arrival_deadline, cargo_type, weight_kg, status
            """;

    @Insert("""
            INSERT INTO estimate (estimate_id, estimate_number, origin_unlocode,
                destination_unlocode, arrival_deadline, cargo_type, weight_kg, status)
            VALUES (#{estimateId}, #{estimateNumber}, #{originUnlocode},
                #{destinationUnlocode}, #{arrivalDeadline}, #{cargoType}, #{weightKg},
                #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(EstimateRecord row);

    @Select("SELECT " + COLUMNS + " FROM estimate WHERE estimate_id = #{estimateId}")
    @Results(id = "estimate", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "estimate_id", property = "estimateId"),
            @Result(column = "estimate_number", property = "estimateNumber"),
            @Result(column = "origin_unlocode", property = "originUnlocode"),
            @Result(column = "destination_unlocode", property = "destinationUnlocode"),
            @Result(column = "arrival_deadline", property = "arrivalDeadline"),
            @Result(column = "cargo_type", property = "cargoType"),
            @Result(column = "weight_kg", property = "weightKg"),
            @Result(column = "status", property = "status"),
    })
    EstimateRecord selectByEstimateId(@Param("estimateId") String estimateId);

    @Select("SELECT " + COLUMNS + " FROM estimate WHERE estimate_number = #{estimateNumber}")
    @ResultMap("estimate")
    EstimateRecord selectByEstimateNumber(@Param("estimateNumber") String estimateNumber);

    /** **新しい順**——直近に作ったものから見る。 */
    @Select("SELECT " + COLUMNS + " FROM estimate ORDER BY id DESC")
    @ResultMap("estimate")
    List<EstimateRecord> selectAll();

    @Insert("""
            INSERT INTO route_candidate (estimate_id, voyage_number, transit_port,
                transit_days, estimated_cost, rank)
            VALUES (#{estimateId}, #{voyageNumber}, #{transitPort}, #{transitDays},
                #{estimatedCost}, #{rank})
            """)
    void insertCandidate(RouteCandidateRecord row);

    /** **推奨順で返す。**順序に意味がある（上から見せる）。 */
    @Select("""
            SELECT c.estimate_id, c.voyage_number, c.transit_port, c.transit_days,
                   c.estimated_cost, c.rank
              FROM route_candidate c
              JOIN estimate e ON e.id = c.estimate_id
             WHERE e.estimate_id = #{estimateId}
             ORDER BY c.rank
            """)
    @Results(id = "routeCandidate", value = {
            @Result(column = "estimate_id", property = "estimateId"),
            @Result(column = "voyage_number", property = "voyageNumber"),
            @Result(column = "transit_port", property = "transitPort"),
            @Result(column = "transit_days", property = "transitDays"),
            @Result(column = "estimated_cost", property = "estimatedCost"),
            @Result(column = "rank", property = "rank"),
    })
    List<RouteCandidateRecord> selectCandidates(@Param("estimateId") String estimateId);

    /**
     * 見積番号を採番する（[ADR-011] と同じ形）。
     *
     * <p><strong>DB のシーケンスに任せる。</strong>MAX+1 の自前採番は、同時に 2 件
     * 作られたときに衝突する。
     */
    @Select("SELECT NEXTVAL('estimate_number_seq')")
    long nextEstimateNumber();
}
