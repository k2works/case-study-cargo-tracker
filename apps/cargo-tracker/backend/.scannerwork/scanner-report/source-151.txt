package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.valueobjects.VoyageSearchCriteria;
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

    /**
     * 更新を反映する（US25）。<b>常に INSERT する形にしない。</b> 状態の更新で行を
     * 増やすと、作成しかないイテレーションでは成立し、最初の更新ストーリーで壊れる。
     *
     * <p>戻り値で「書けたか」を見る。0 なら投影にその航海が無い。</p>
     */
    int updateSchedule(VoyageRow row);

    /** 寄港地は全行を入れ替える（data-model.md）。足すだけだと古い区間が残る。 */
    int deleteMovements(@Param("voyageNumber") String voyageNumber);

    /** 受入種別も入れ替える。追記だけだと外した種別が残る。 */
    int deleteAcceptedCargoTypes(@Param("voyageNumber") String voyageNumber);

    int insertAcceptedCargoType(@Param("voyageNumber") String voyageNumber,
            @Param("cargoType") String cargoType);

    VoyageRow findByNumber(@Param("voyageNumber") String voyageNumber);

    /**
     * 一覧（S32）。既定では出港済みとキャンセルを外し、出発日が近い順に並べる
     * （ui_design.md）。出港してしまった便が混ざると、一覧全体が「これから使える航海」
     * として信用されなくなる。
     *
     * <p>検索条件（US07）は<b>既定の絞り込みと組み合わせる</b>。条件で置き換えると、
     * 出港済みの航海が検索結果にだけ戻る。</p>
     */
    List<VoyageRow> findAll(@Param("includeFinished") boolean includeFinished,
            @Param("criteria") VoyageSearchCriteria criteria,
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("offset") int offset);

    int countAll(@Param("includeFinished") boolean includeFinished,
            @Param("criteria") VoyageSearchCriteria criteria,
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
            String lastEventId,
            Instant updatedAt,
            String updatedBy) {
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
