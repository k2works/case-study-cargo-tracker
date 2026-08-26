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
 *
 * <p><strong>すでに精算済なら、何もせず成功として返す（冪等）。</strong>
 * 相手（billingms）は入金の記録と同じ取引の中でこれを呼ぶ。通知が届いたあとに
 * 相手側が失敗すると、<strong>予約だけが精算済で、請求書は未入金のまま</strong>残る
 * ——そこで断ると、経理担当者は何度押しても入金を記録できない
 * （IT12 レビュー・architect 高 1）。<strong>「入金が 2 件か操作の重複か」を
 * 見分けるのは請求書の側の仕事である</strong>（同じ請求書に二度は確認できない）。
 */
public class SettleBookingUseCase {

    private final CargoRepository cargoes;

    public SettleBookingUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    /**
     * 予約を精算済にする。
     *
     * <p><strong>冪等である。</strong>すでに精算済なら何もしない——2 回目が来るのは
     * 相手側の再試行であり、こちらが断ると入金を永久に記録できなくなる。
     */
    @Transactional
    public void settle(String bookingId) {
        CargoSummary summary = cargoes.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "予約が見つかりません: " + bookingId));

        if (summary.cargo().isSettled()) {
            return;
        }
        cargoes.save(summary.cargo().settle());
    }
}
