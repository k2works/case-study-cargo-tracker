package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 航海の投影テーブル（data-model.md「routing_read_db」）。 */
@Mapper
public interface VoyageMapper {

    /**
     * 一意制約は例外ではなく戻り値で見る（{@code ON CONFLICT DO NOTHING}）。
     *
     * <p>例外にすると PostgreSQL がトランザクションを中断し、捕まえても外側の投影と
     * トークンが書けなくなって、その 1 件で投影全体が止まる（IT2 で実測）。</p>
     */
    int insert(VoyageRow row);

    int insertMovement(MovementRow row);

    int insertAcceptedCargoType(@Param("voyageNumber") String voyageNumber,
            @Param("cargoType") String cargoType);

    VoyageRow findByNumber(@Param("voyageNumber") String voyageNumber);

    /**
     * 一覧（S32）。既定では出港済みとキャンセルを外し、出発日が近い順に並べる
     * （ui_design.md）。出港してしまった便が混ざると、一覧全体が「これから使える航海」
     * として信用されなくなる。
     *
     * <p>{@code cargoType} を与えると、その種別を受け入れる航海だけに絞る（US05）。</p>
     */
    List<VoyageRow> findAll(@Param("includeFinished") boolean includeFinished,
            @Param("cargoType") String cargoType,
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("offset") int offset);

    int countAll(@Param("includeFinished") boolean includeFinished,
            @Param("cargoType") String cargoType,
            @Param("now") Instant now);

    List<String> findAcceptedCargoTypes(@Param("voyageNumber") String voyageNumber);

    List<MovementRow> findMovements(@Param("voyageNumber") String voyageNumber);

    /** 投影の行。 */
    record VoyageRow(
            String voyageNumber,
            String carrierCode,
            String carrierName,
            String vesselName,
            String departureUnlocode,
            String arrivalUnlocode,
            Instant departureAt,
            Instant arrivalAt,
            boolean cancelled,
            Instant registeredAt,
            Instant projectedAt,
            String lastEventId) {
    }

    /** 港間移動の行。並び順は {@code movementSeq}。 */
    record MovementRow(
            String voyageNumber,
            int movementSeq,
            String departureUnlocode,
            String arrivalUnlocode,
            Instant departureAt,
            Instant arrivalAt) {
    }
}
