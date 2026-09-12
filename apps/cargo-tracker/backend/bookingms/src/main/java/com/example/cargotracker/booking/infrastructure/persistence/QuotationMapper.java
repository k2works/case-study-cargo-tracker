package com.example.cargotracker.booking.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 見積の読み取りモデル（data-model.md「booking_read_db」・US01）。 */
@Mapper
public interface QuotationMapper {

    /**
     * 読み出す列を並べる。
     *
     * <p><b>{@code SELECT *} にしない</b>——注釈マッパー + record は列名ではなく
     * <b>位置</b>で割り当てるので、列を足した瞬間に項目が丸ごとずれる。並びは
     * record の構築子と同じにする。</p>
     */
    String COLUMNS = "quotation_id, origin_unlocode, destination_unlocode, arrival_deadline, "
            + "cargo_type, weight_kg, estimated_amount, estimated_currency, valid_until, "
            + "created_by, created_at, projected_at";

    /**
     * 冪等に書く。少なくとも 1 回配送なので、同じイベントが 2 度届きうる。
     *
     * <p>見積は<b>作られたあと変わらない</b>ので、衝突したら見送る。</p>
     */
    int insert(QuotationRow row);

    @Select("SELECT " + COLUMNS + " FROM quotation WHERE quotation_id = #{quotationId}")
    QuotationRow find(@Param("quotationId") String quotationId);

    /**
     * 候補を入れ直す前に消す。
     *
     * <p><b>追記専用の行はリプレイで増える</b>（IT6 で実際に踏んだ）。候補は
     * 見積ごとに作り直す。</p>
     */
    @Delete("DELETE FROM quotation_candidate WHERE quotation_id = #{quotationId}")
    int deleteCandidates(@Param("quotationId") String quotationId);

    int insertCandidate(CandidateRow row);

    /** 提示した順に返す。<b>順序が業務の意味を持つ</b>（安い順・間に合う順）。 */
    @Select("SELECT quotation_id, candidate_seq, voyage_numbers, ports, transit_days, "
            + "estimated_cost, estimated_currency, overdue_days FROM quotation_candidate "
            + "WHERE quotation_id = #{quotationId} ORDER BY candidate_seq")
    List<CandidateRow> findCandidates(@Param("quotationId") String quotationId);

    record QuotationRow(
            String quotationId,
            String originUnLocode,
            String destinationUnLocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            BigDecimal estimatedAmount,
            String estimatedCurrency,
            LocalDate validUntil,
            String createdBy,
            Instant createdAt,
            Instant projectedAt) {
    }

    record CandidateRow(
            String quotationId,
            int candidateSeq,
            String voyageNumbers,
            String ports,
            int transitDays,
            BigDecimal estimatedCost,
            String estimatedCurrency,
            int overdueDays) {
    }
}
