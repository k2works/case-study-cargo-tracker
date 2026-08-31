package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.aggregates.Cargo;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 経路設計を依頼する（US06）。
 *
 * <p>経路設計者への通知（メール）は仕組みが無いため送らない。代わりに、経路設計者が
 * 自分のダッシュボードで「経路設計待ち」に気づけるようにする。気づく手段だけを置いて
 * そこから対象へ行けないと、件数を見ても仕事は進まない。
 */
@Service
public class RequestRoutingUseCase {

    private final CargoRepository cargoes;

    public RequestRoutingUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    /**
     * @return 依頼後の予約。予約が見つからなければ空
     * @throws IllegalStateException 依頼できない状態のとき（理由は集約が持つ）
     */
    public Optional<CargoSummary> request(String bookingId) {
        Optional<CargoSummary> found = cargoes.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Cargo requested = found.get().cargo().requestRouting();
        return Optional.of(new CargoSummary(cargoes.save(requested), found.get().shipperName(),
                found.get().shipperCode()));
    }
}
