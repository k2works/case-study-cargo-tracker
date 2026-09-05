package com.example.cargotracker.billing.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 荷主の契約スナップショット（data-model.md「billing_read_db」）。 */
@Mapper
public interface ShipperContractSnapshotMapper {

    /**
     * 冪等に書く。少なくとも 1 回配送なので、同じイベントが 2 度届きうる。
     * リプレイでも同じ結果になるよう、常に上書きする。
     */
    int upsert(SnapshotRow row);

    @Select("SELECT count(*) FROM shipper_contract_snapshot")
    int count();

    SnapshotRow find(@Param("shipperId") String shipperId);

    record SnapshotRow(
            String shipperId,
            String shipperName,
            String shipperType,
            BigDecimal discountRate,
            String contractNumber,
            Instant projectedAt,
            String lastEventId) {
    }
}
