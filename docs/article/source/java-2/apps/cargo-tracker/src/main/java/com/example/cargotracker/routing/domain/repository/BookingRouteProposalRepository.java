package com.example.cargotracker.routing.domain.repository;

import com.example.cargotracker.routing.domain.model.aggregates.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingBookingId;
import java.util.Optional;

/** 経路提案の出力ポート。実装はインフラ層に置く（DIP）。 */
public interface BookingRouteProposalRepository {

    /**
     * 提案を保存する。予約 1 件につき 1 つであり、<strong>再算出は上書きする</strong>。
     *
     * <p>候補は<strong>丸ごと入れ替える</strong>（ビジネスルール 5）。
     * 前回の候補が残ると、どの候補がどの条件で出たものか分からなくなる。
     *
     * @throws java.util.ConcurrentModificationException 別の担当者が先に算出していた場合
     *     （楽観的ロック。<strong>黙って上書きしない</strong>）
     */
    void save(BookingRouteProposal proposal);


    Optional<BookingRouteProposal> findByBookingId(RoutingBookingId bookingId);
}
