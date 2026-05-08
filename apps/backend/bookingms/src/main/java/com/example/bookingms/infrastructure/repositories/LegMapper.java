package com.example.bookingms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis マッパーインターフェース（旅程区間）
 */
@Mapper
public interface LegMapper {

    /**
     * leg テーブルに1件挿入する
     *
     * @param legRecord 挿入する LegRecord
     */
    void insertLeg(LegRecord legRecord);

    /**
     * cargo_id に紐づく leg を取得する
     *
     * @param cargoId 貨物 ID
     * @return LegRecord リスト（seq_number 昇順）
     */
    List<LegRecord> findByCargoId(@Param("cargoId") Long cargoId);

    /**
     * cargo_id に紐づく leg を全件削除する（経路再割当時）
     *
     * @param cargoId 貨物 ID
     */
    void deleteByCargoId(@Param("cargoId") Long cargoId);
}
