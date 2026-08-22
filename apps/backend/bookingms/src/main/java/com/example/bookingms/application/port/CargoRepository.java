package com.example.bookingms.application.port;

import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.RoutingStatus;
import java.util.Collection;
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

    /** 予約番号から探す。画面の URL に出るのは予約番号であり、内部の id ではない。 */
    Optional<CargoSummary> findByBookingId(String bookingId);

    /**
     * 一覧を新しい順に返す。
     *
     * @param type 貨物種別での絞り込み（null なら全種別）
     * @param keyword 予約番号・荷主名での絞り込み（null なら全件）
     * @param routingStatus 経路の状況での絞り込み（null なら全件）。経路設計待ちだけを
     *     取り出すために要る。件数だけ出しても、そこから対象へ行けなければ仕事は進まない
     * @param limit 返す件数の上限。上限が無いと、件数が増えた日に一覧が開かなくなる
     */
    /**
     * 一覧を引く。
     *
     * @param routingStatuses 経路の状態での絞り込み。<strong>空または {@code null} は
     *     「絞らない」</strong>（「どの状態にも当てはまらない」ではない）
     */
    List<CargoSummary> search(CargoType type, String keyword,
            Collection<RoutingStatus> routingStatuses, int limit);

    /** 絞り込み条件に合う総件数。上限で切った一覧が全体の何件中かを示すために要る。 */
    long count(CargoType type, String keyword, Collection<RoutingStatus> routingStatuses);
}
