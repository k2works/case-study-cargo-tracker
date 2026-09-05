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

    /**
     * キャンセルを反映する（US24）。<b>行は消さない。</b> その航海で経路を組んだ
     * 貨物があるので、消すと何が起きたのかを追えなくなる。
     *
     * <p>戻り値で「書けたか」を見る。0 なら投影にその航海が無い。</p>
     */
    int cancel(@Param("voyageNumber") String voyageNumber,
            @Param("cancelledAt") Instant cancelledAt,
            @Param("cancelReason") String cancelReason,
            @Param("cancelledBy") String cancelledBy,
            @Param("projectedAt") Instant projectedAt);

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

    /**
     * 経路探索が見る区間（US08）。
     *
     * <p><b>一覧（{@code visible}）とは別の条件である。</b> 一覧は航海の出港
     * （{@code voyage.departure_at}）で見るが、探索は<b>区間の出発</b>で見る。
     * 航海はもう出ていても、まだ出ていない後半の区間には積めるためで、
     * {@code visible} をそのまま使うと積める区間まで落とす。</p>
     *
     * <p>両方で守るのは「キャンセル済みを外す」だけである。走らない船で経路を
     * 組ませない。<b>一覧に絞り込みを足しても、ここは自動では追随しない。</b>
     * 追随させるべき条件かどうかを、足すときに判断すること。</p>
     *
     * <p>受入貨物種別はここでは返さない。集約関数で 1 行に畳むと方言に寄る。
     * 呼ぶ側（{@code ProjectionVoyageGraph}）が航海ごとに 1 度だけ引いて覚える。</p>
     */
    List<TransitEdgeRow> findEdgesFrom(@Param("unLocode") String unLocode,
            @Param("now") Instant now);

    /**
     * 投影が知っている港か（US08）。
     *
     * <p>書式が正しくても登録の無い港（{@code JPXXX} など）は、黙って候補 0 件に
     * なる。条件の打ち間違いが「経路が無い」と読めると、経路設計者は直らない条件を
     * 変え続けることになる。</p>
     *
     * <p>キャンセル・出港済みは見ない。<b>港の存在は航海の状態と別</b>である
     * （止まった便しか無い港も、港としては知っている）。</p>
     */
    boolean existsPort(@Param("unLocode") String unLocode);

    /** 探索が見る 1 区間。{@code voyage} と {@code carrier_movement} を結んだ形。 */
    record TransitEdgeRow(
            String voyageNumber,
            String departureUnlocode,
            String arrivalUnlocode,
            Instant departureAt,
            Instant arrivalAt) {
    }

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
            String updatedBy,
            // キャンセル（US24）。止めていなければ 3 つとも null。
            Instant cancelledAt,
            String cancelReason,
            String cancelledBy) {
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
