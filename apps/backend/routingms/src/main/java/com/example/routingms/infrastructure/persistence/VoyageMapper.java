package com.example.routingms.infrastructure.persistence;

import com.example.routingms.infrastructure.persistence.record.CarrierMovementRecord;
import com.example.routingms.infrastructure.persistence.record.VoyageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis マッパーインターフェース（航海）
 */
@Mapper
public interface VoyageMapper {

    /**
     * voyage テーブルに1件挿入する
     *
     * @param record 挿入する VoyageRecord
     */
    void insertVoyage(VoyageRecord record);

    /**
     * carrier_movement テーブルに1件挿入する
     *
     * @param record 挿入する CarrierMovementRecord
     */
    void insertCarrierMovement(CarrierMovementRecord record);

    /**
     * 航海番号で voyage を検索する
     *
     * @param voyageNumber 航海番号
     * @return VoyageRecord（存在しない場合は空）
     */
    Optional<VoyageRecord> findVoyageByNumber(@Param("voyageNumber") String voyageNumber);

    /**
     * voyage_id に紐づく carrier_movement を取得する
     *
     * @param voyageId voyage の id
     * @return CarrierMovementRecord リスト（seq_number 昇順）
     */
    List<CarrierMovementRecord> findCarrierMovementsByVoyageId(@Param("voyageId") Long voyageId);

    /**
     * すべての voyage を取得する
     *
     * @return VoyageRecord リスト
     */
    List<VoyageRecord> findAllVoyages();

    /**
     * voyage の updated_at を更新する（voyage_number で特定）
     *
     * @param record 更新対象の VoyageRecord（id, updated_at を使用）
     */
    void updateVoyage(VoyageRecord record);

    /**
     * voyage_id に紐づく carrier_movement を全件削除する
     *
     * @param voyageId voyage の id
     */
    void deleteCarrierMovementsByVoyageId(@Param("voyageId") Long voyageId);

    /**
     * 航海番号で voyage を削除する
     *
     * @param voyageNumber 航海番号
     */
    void deleteVoyageByNumber(@Param("voyageNumber") String voyageNumber);
}
