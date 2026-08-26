package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 精算の完了を受けて予約を閉じる（US23-4・[ADR-028] 決定 1）。
 *
 * <p><strong>bookingms は自分では知らない。</strong>請求と入金は経理の仕事であり、
 * 予約の側に判断材料が無い。入金を確認した billingms が知らせてくる。
 *
 * <p><strong>知らない予約や引取前の予約は断る。</strong>荷役の購読
 * （{@code AdvanceBookingUseCase}）が「止めない」のとは立場が違う——あちらは一覧の
 * 見え方であり、こちらは<strong>相手が「精算が閉じた」と信じる根拠</strong>である。
 * 黙って捨てると、引取済のまま残った予約に誰も気づけない。
 */
public class SettleBookingUseCase {

    private final CargoRepository cargoes;

    public SettleBookingUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    /**
     * 予約を精算済にする。
     *
     * <p><strong>冪等ではない。</strong>すでに精算済の予約に 2 回目が来たら断る
     * ——入金が 2 件あったのか操作を重ねただけなのかは、こちらでは判断できない。
     */
    @Transactional
    public void settle(String bookingId) {
        CargoSummary summary = cargoes.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "予約が見つかりません: " + bookingId));

        cargoes.save(summary.cargo().settle());
    }
}
