package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.Cargo;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 経路を荷主へ通知する（US12・[ADR-021] 決定 1・決定 2）。
 *
 * <p>状態遷移と可否の判定は集約が持つ。ここは「見つけて、集約に頼んで、保存する」だけである。
 *
 * <p><strong>メールは送らない。</strong>通知の仕組みは US19（通知基盤）で入る。ここで残すのは
 * 「通知したという業務上の事実」であり、それを画面が見せる。代替であることは画面とマニュアルに
 * 明記する。
 */
@Service
public class NotifyShipperUseCase {

    private final CargoRepository cargoes;
    private final Clock clock;

    public NotifyShipperUseCase(CargoRepository cargoes, Clock clock) {
        this.cargoes = cargoes;
        this.clock = clock;
    }

    /**
     * 通知する。予約が見つからなければ空を返す。
     *
     * @param notifiedBy 通知した担当者の利用者 ID。<strong>呼び出し側が名乗る</strong>
     *     （記録に残すのは「誰が」であり、システムではない）
     */
    public Optional<Cargo> notifyShipper(String bookingId, String notifiedBy) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> cargoes.save(cargo.notifyShipper(clock.instant(), notifiedBy)));
    }
}
