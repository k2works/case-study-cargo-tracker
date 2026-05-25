package com.example.routingms.infrastructure.repositories.mybatis;

import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RouteDesignRequestMapper {

    void insert(@Param("bookingId") String bookingId,
                @Param("originUnlocode") String originUnlocode,
                @Param("destinationUnlocode") String destinationUnlocode,
                @Param("arrivalDeadline") LocalDate arrivalDeadline,
                @Param("cargoType") String cargoType);

    RouteDesignRequestProjection findByBookingId(@Param("bookingId") String bookingId);

    List<RouteDesignRequestProjection> findAll();
}
