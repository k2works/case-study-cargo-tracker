package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.Cargo;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役の記録を受けて予約を進める（US30・[ADR-025] 決定 1）。
 *
 * <p><strong>bookingms は自分では「輸送中」を知らない。</strong>荷役の記録が一次情報で
 * ある。この購読が無いあいだ、予約一覧は<strong>船に載った貨物を「受領待ち」と出し
 * 続けていた</strong>——`transport_status` は IT2（[ADR-009]）からあるのに、更新する者が
 * 誰もいなかった。
 *
 * <p><strong>ACL を引かない。</strong>相手のドメインを読みに行くのではなく、イベントが
 * 運ぶものだけで進める。
 *
 * <p><strong>知らない追跡番号では止まらない。</strong>例外にすると、後続の荷役イベントも
 * 処理されなくなる。ここが守るのは「予約一覧の見え方」であり、止めるほどのものではない
 * （{@code AdvanceTrackingUseCase} と同じ立場）。
 */
public class AdvanceBookingUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdvanceBookingUseCase.class);

    private final CargoRepository cargoes;

    public AdvanceBookingUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    /**
     * 荷役の記録に応じて予約を進める。
     *
     * <p><strong>冪等である。</strong>再試行がある以上、同じイベントが 2 回届くのは普通の
     * ことである。何も変わらないなら書かない——毎回書くと、変化していない更新が記録に積まれる。
     *
     * <p><strong>巻き戻さない。</strong>判定は集約が持つ（{@code Cargo#afterHandling}）。
     */
    @Transactional
    public void advance(String trackingNumber, String handlingType, String locationUnLocode,
            Instant completionTime) {
        cargoes.findByTrackingNumber(trackingNumber)
                .map(CargoSummary::cargo)
                .ifPresentOrElse(
                        cargo -> save(cargo, handlingType, locationUnLocode, completionTime),
                        () -> log.info("荷役のイベントに一致する予約がありません: trackingNumber={}",
                                trackingNumber));
    }

    private void save(Cargo cargo, String handlingType, String locationUnLocode, Instant at) {
        Cargo advanced = cargo.afterHandling(handlingType, locationUnLocode, at);
        if (advanced == cargo) {
            // 集約が「動かない」と答えた。**書かない**——何も変わっていない更新を積まない
            return;
        }
        cargoes.save(advanced);
    }
}
