package com.example.cargotracker.exception.domain.model.repository;

import com.example.cargotracker.exception.domain.model.aggregates.CargoIncident;

import java.util.List;

/**
 * 貨物例外リポジトリインターフェース。
 */
public interface CargoExceptionRepository {

    void save(CargoIncident incident);

    List<CargoIncident> findByTrackingNumber(String trackingNumber);
}
