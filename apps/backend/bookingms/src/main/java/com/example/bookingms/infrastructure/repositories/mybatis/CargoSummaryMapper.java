package com.example.bookingms.infrastructure.repositories.mybatis;

import com.example.bookingms.domain.projections.CargoSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 貨物予約 Read Model 用の MyBatis Mapper。
 *
 * <p>SQL は {@code resources/mapper/CargoSummaryMapper.xml} で定義する。
 * US05 で hazard_* / temperature_* パラメータを追加。</p>
 */
@Mapper
public interface CargoSummaryMapper {

    @SuppressWarnings("java:S107") // MyBatis Mapper は SQL の全カラムをパラメータに必要とするため許容
    void insertCargoSummary(@Param("bookingId") String bookingId,
                            @Param("shipperId") String shipperId,
                            @Param("originUnlocode") String originUnlocode,
                            @Param("destinationUnlocode") String destinationUnlocode,
                            @Param("arrivalDeadline") LocalDate arrivalDeadline,
                            @Param("cargoType") String cargoType,
                            @Param("weightKg") BigDecimal weightKg,
                            @Param("lengthCm") Integer lengthCm,
                            @Param("widthCm") Integer widthCm,
                            @Param("heightCm") Integer heightCm,
                            @Param("quantity") Integer quantity,
                            @Param("productName") String productName,
                            @Param("hazardImoClass") String hazardImoClass,
                            @Param("hazardUnNumber") String hazardUnNumber,
                            @Param("hazardDeclaration") String hazardDeclaration,
                            @Param("temperatureMinC") BigDecimal temperatureMinC,
                            @Param("temperatureMaxC") BigDecimal temperatureMaxC,
                            @Param("bookingStatus") String bookingStatus,
                            @Param("routingStatus") String routingStatus);

    CargoSummary findByBookingId(@Param("bookingId") String bookingId);

    List<CargoSummary> findAllPaged(@Param("offset") int offset, @Param("limit") int limit);

    long countAll();
}
