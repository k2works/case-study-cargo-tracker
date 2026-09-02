package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.aggregates.Cargo;
import java.time.Instant;
import org.springframework.stereotype.Service;

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
@Service
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
            Instant completionTime, boolean offRoute) {
        cargoes.findByTrackingNumber(trackingNumber)
                .map(CargoSummary::cargo)
                .ifPresentOrElse(
                        cargo -> save(cargo, handlingType, locationUnLocode, completionTime,
                                offRoute),
                        () -> log.info("荷役のイベントに一致する予約がありません: trackingNumber={}",
                                trackingNumber));
    }

    /**
     * 予約を進め、<strong>予定ルート外なら誤配として記録する</strong>
     * （US28-2・[ADR-026] 決定 1）。
     *
     * <p><strong>判定は handlingms が済ませている。</strong>{@code offRoute} は旅程と作業
     * 場所を照合した結果であり（[ADR-023] 決定 3）、ここで判定し直さない
     * ——旅程の写しをもう 1 つ持つと、片方だけが古い旅程で判定する。
     *
     * <p><strong>誤配でも状態は進む。</strong>予定外の港で降ろされても、荷役は起きている。
     * 進めないと、貨物が動いているのに予約は「受領待ち」のままになる。
     */
    private void save(Cargo cargo, String handlingType, String locationUnLocode, Instant at,
            boolean offRoute) {
        Cargo advanced = cargo.afterHandling(handlingType, locationUnLocode, at);
        Cargo result = offRoute ? advanced.misrouted(locationUnLocode, at) : advanced;
        if (result == cargo) {
            // 集約が「動かない」と答えた。**書かない**——何も変わっていない更新を積まない
            return;
        }
        cargoes.save(result);
    }
}
