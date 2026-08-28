package com.example.bookingms.domain.repository;

import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RoutingStatus;
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

    /**
     * 追跡番号を採番する（US14-2）。
     *
     * <p><strong>組み立てるのは永続化の側である</strong>（[ADR-011] と同じ形）。集約や
     * ユースケースで文字列を作ると、別の経路が違う形式を発行できてしまい、サービスを
     * またいだ照合が壊れる。
     */
    String nextTrackingNumber();

    /** 予約番号から探す。画面の URL に出るのは予約番号であり、内部の id ではない。 */
    Optional<CargoSummary> findByBookingId(String bookingId);

    /**
     * 追跡番号から探す（US15-1）。
     *
     * <p>荷役作業員は予約番号を知らない。手元にあるのは貨物に貼られた追跡番号である。
     * handlingms はこれを起点に貨物を特定する（[ADR-023] 決定 2）。
     */
    Optional<CargoSummary> findByTrackingNumber(String trackingNumber);

    /**
     * 一覧を新しい順に返す。
     *
     * @param type 貨物種別での絞り込み（null なら全種別）
     * @param keyword 予約番号・荷主名での絞り込み（null なら全件）
     * @param routingStatuses 経路の状態での絞り込み。<strong>空または {@code null} は
     *     「絞らない」</strong>（「どの状態にも当てはまらない」ではない）。経路設計者に
     *     見せる範囲を絞るために要る。件数だけ出しても、そこから対象へ行けなければ
     *     仕事は進まない
     * @param bookingStatus 予約の状態での絞り込み（null なら絞らない）。経路設計者が
     *     「追跡番号の発行を待っている予約」を取り出すために要る（US13-3）
     * @param limit 返す件数の上限。上限が無いと、件数が増えた日に一覧が開かなくなる
     */
    List<CargoSummary> search(CargoType type, String keyword,
            Collection<RoutingStatus> routingStatuses, BookingStatus bookingStatus, int limit);

    /**
     * 絞り込み条件に合う総件数。上限で切った一覧が全体の何件中かを示すために要る。
     *
     * @param routingStatuses 経路の状態での絞り込み。空または {@code null} は「絞らない」
     */
    long count(CargoType type, String keyword, Collection<RoutingStatus> routingStatuses,
            BookingStatus bookingStatus);
}
