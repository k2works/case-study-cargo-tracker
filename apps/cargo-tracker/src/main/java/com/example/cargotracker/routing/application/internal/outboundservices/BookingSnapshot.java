package com.example.cargotracker.routing.application.internal.outboundservices;

import com.example.cargotracker.routing.domain.model.CargoType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * routing コンテキストが booking コンテキストから必要とする予約情報のスナップショット。
 *
 * <p>booking コンテキストへの直接依存を避けるため、
 * {@link BookingQueryPort} 経由で取得するデータ転送オブジェクト。
 */
public record BookingSnapshot(
    String originLocode,
    String destinationLocode,
    LocalDate requestedArrivalDate,
    CargoType cargoType,
    BigDecimal weightKg
) {}
