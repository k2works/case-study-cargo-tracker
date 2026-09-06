package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.application.port.TrackingNumberGenerator;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * {@link TrackingNumberGenerator} の実装。データベースのシーケンスで採る。
 *
 * <p><b>業務タイムゾーンの「今日」で年を決める。</b> UTC で採ると、日本時間の朝 9 時より
 * 前に発行した番号だけ前年になる時間帯が、年末に生まれる。</p>
 */
@Component
public class SequenceTrackingNumberGenerator implements TrackingNumberGenerator {

    private final CargoSummaryMapper cargos;
    private final Clock clock;

    public SequenceTrackingNumberGenerator(CargoSummaryMapper cargos, Clock clock) {
        this.cargos = cargos;
        this.clock = clock;
    }

    @Override
    public String next() {
        return cargos.nextTrackingNumber(LocalDate.now(clock));
    }
}
