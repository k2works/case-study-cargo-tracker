package com.example.cargotracker.routing.domain.model.repository;

import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.model.Location;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VoyageRepository {

    void save(Voyage voyage);

    Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber);

    /**
     * 出発地・目的地・出発期間・到着期限でフィルタして航海を検索する。
     *
     * @param origin       出発地（null の場合はすべての出発地）
     * @param destination  目的地（null の場合はすべての目的地）
     * @param fromDate     出発期間開始日（null の場合は制限なし）
     * @param arrivalDeadline 到着期限（この日までに到着できる航海のみ）
     * @return 条件に合致する航海一覧
     */
    List<Voyage> findByRoute(Location origin, Location destination,
                              LocalDate fromDate, LocalDate arrivalDeadline);

    List<Voyage> findAll();
}
