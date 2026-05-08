package com.example.bookingms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis マッパーインターフェース（貨物）
 */
@Mapper
public interface CargoMapper {

    /**
     * cargo テーブルに1件挿入する
     *
     * @param cargoRecord 挿入する CargoRecord
     */
    void insertCargo(CargoRecord cargoRecord);

    /**
     * 予約 ID で cargo を検索する
     *
     * @param bookingId 予約 ID
     * @return CargoRecord（存在しない場合は空）
     */
    Optional<CargoRecord> findByBookingId(@Param("bookingId") String bookingId);

    /**
     * すべての cargo を取得する
     *
     * @return CargoRecord リスト
     */
    List<CargoRecord> findAll();

    /**
     * cargo のステータスと routing_status を更新する
     *
     * @param cargoRecord 更新する CargoRecord（id, bookingStatus, routingStatus が必須）
     */
    void updateCargo(CargoRecord cargoRecord);
}
