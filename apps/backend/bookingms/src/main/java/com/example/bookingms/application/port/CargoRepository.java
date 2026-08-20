package com.example.bookingms.application.port;

import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoType;
import java.util.List;
import java.util.Optional;

public interface CargoRepository {

    /**
     * 予約を保存し、採番された予約番号を含む状態を返す。
     *
     * <p>採番は DB が行う（ADR-011）。呼び出し側で番号を組み立てない。
     */
    Cargo save(Cargo cargo);

    Optional<Cargo> findById(Long id);

    /**
     * 一覧を新しい順に返す。
     *
     * @param type 貨物種別での絞り込み（null なら全種別）
     * @param keyword 予約番号・荷主名での絞り込み（null なら全件）
     * @param limit 返す件数の上限。上限が無いと、件数が増えた日に一覧が開かなくなる
     */
    List<CargoSummary> search(CargoType type, String keyword, int limit);

    /** 絞り込み条件に合う総件数。上限で切った一覧が全体の何件中かを示すために要る。 */
    long count(CargoType type, String keyword);
}
