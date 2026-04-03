package com.example.cargotracker.exception.domain.model.repository;

import com.example.cargotracker.exception.domain.model.aggregates.CargoException;

import java.util.List;

/**
 * 貨物例外リポジトリインターフェース。
 */
public interface CargoExceptionRepository {

    void save(CargoException cargoException);

    List<CargoException> findByTrackingNumber(String trackingNumber);
}
