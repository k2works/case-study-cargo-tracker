package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.domain.event.VoyageRescheduledEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 航海の更新を区間の「いまの日程」に写す（US25 / IT12 持ち越し C3）。
 *
 * <p>IT11 までは予約詳細が {@code voyage} / {@code carrier_movement} を JOIN して
 * いまの日程を読んでいた。どちらも Routing の持ち物であり、
 * <strong>BC をまたぐ結合だった</strong>（ADR-015 の許容リストに「次に返す候補」として
 * 名前を残していたもの）。航海の更新を購読して自分のテーブルに写せば、
 * 予約が読むのは自分の BC のテーブルだけになる。
 *
 * <p><strong>当初の日程は動かさない。</strong> 当初と現在の差が「日程が変わりました」の
 * 印そのものである。両方を上書きすると、何が変わったのか分からなくなる。
 *
 * <p><strong>経路の作り直しはしない。</strong> 利用者の知らないうちに経路が変わる
 * （再設計は US28 の領分である）。ここでやるのは<strong>日程の写しだけ</strong>である。
 */
@Service
public class SyncItineraryScheduleCommandService {

    private final CargoRepository repository;

    public SyncItineraryScheduleCommandService(CargoRepository repository) {
        this.repository = repository;
    }

    /**
     * 届いた日程を写す。
     *
     * @return 写した区間の数。<strong>0 件は「その便を使う予約が無い」ことを表し、
     *         異常ではない</strong>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sync(VoyageRescheduledEvent event) {
        int synced = 0;
        for (VoyageRescheduledEvent.MovementSchedule movement : event.movements()) {
            synced += repository.syncCurrentSchedule(
                    event.voyageNumber(),
                    movement.departureUnlocode(),
                    movement.arrivalUnlocode(),
                    movement.departureTime(),
                    movement.arrivalTime());
        }
        return synced;
    }
}
