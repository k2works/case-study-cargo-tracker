package com.example.handlingms.infrastructure.repositories.mybatis;

import com.example.handlingms.domain.projections.HandlingActivitySummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * handling_activity Read Model 用 Mapper（US15・US16 / IT5 3.x）。
 *
 * <p>SQL は {@code resources/mapper/HandlingActivityMapper.xml} で定義する。</p>
 */
@Mapper
public interface HandlingActivityMapper {

    @SuppressWarnings("java:S107") // MyBatis Mapper のパラメータは @Param 個別バインドが標準パターン
    void insert(@Param("activityId") String activityId,
                @Param("bookingId") String bookingId,
                @Param("trackingNumber") String trackingNumber,
                @Param("originUnlocode") String originUnlocode,
                @Param("destinationUnlocode") String destinationUnlocode,
                @Param("cargoType") String cargoType,
                @Param("handlingType") String handlingType,
                @Param("occurredAt") LocalDateTime occurredAt,
                @Param("unlocode") String unlocode,
                @Param("voyageNumber") String voyageNumber,
                @Param("handlerId") String handlerId,
                @Param("unexpected") boolean unexpected);

    HandlingActivitySummary findById(@Param("activityId") String activityId);

    List<HandlingActivitySummary> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    List<HandlingActivitySummary> findAll(@Param("offset") int offset, @Param("limit") int limit);

    long count();

    /** 重複登録防止用：同一 trackingNumber + handlingType + unlocode + 5 分粒度の件数。 */
    long countDuplicates(@Param("trackingNumber") String trackingNumber,
                         @Param("handlingType") String handlingType,
                         @Param("unlocode") String unlocode,
                         @Param("windowStart") LocalDateTime windowStart,
                         @Param("windowEnd") LocalDateTime windowEnd);
}
