package com.example.bookingms.infrastructure.repositories.mybatis;

import com.example.bookingms.domain.projections.ShipperProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 荷主 Read Model 用の MyBatis Mapper。
 *
 * <p>SQL は {@code resources/mapper/ShipperMapper.xml} で定義する。</p>
 */
@Mapper
public interface ShipperMapper {

    @SuppressWarnings("java:S107") // MyBatis Mapper は SQL の全カラムをパラメータに必要とするため許容
    void insertShipper(@Param("shipperId") String shipperId,
                       @Param("shipperType") String shipperType,
                       @Param("name") String name,
                       @Param("addressLine1") String addressLine1,
                       @Param("addressLine2") String addressLine2,
                       @Param("city") String city,
                       @Param("countryCode") String countryCode,
                       @Param("postalCode") String postalCode,
                       @Param("email") String email,
                       @Param("phone") String phone);

    ShipperProjection findByShipperId(@Param("shipperId") String shipperId);

    List<ShipperProjection> findByEmail(@Param("email") String email);

    List<ShipperProjection> findAll();
}
