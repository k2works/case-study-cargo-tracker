package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.port.BillableCargo;
import com.example.bookingms.application.port.BillableCargoFinder;
import com.example.shared.auth.AuthenticatedUser;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 料金算出の入力を返す（US21・[ADR-027] 決定 7）。
 *
 * <p>呼ぶのは billingms であり、人ではない。{@link CargoLookupController}（handlingms 向け）
 * と<strong>同じ形</strong>にする——終盤で新しい結合方式を発明しない（開発戦略）。
 *
 * <p><strong>誤配の記録を載せる</strong>（IT10 レビューの懸念）。IT10 までは予約詳細にしか
 * 出ておらず、経理担当者はその画面を開けなかった——<strong>「残っている」と「読める」は
 * 別である</strong>。
 *
 * <p><strong>参照のみで副作用が無い。</strong>請求のために貨物の状態を変えることはない。
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BillingLookupController {

    /**
     * この入口を呼んでよいサービス。
     *
     * <p><strong>名簿に無い主体は通さない</strong>（[ADR-015] 以来の許可リスト方式）。
     * handlingms は荷役の照会には通るが、<strong>ここは読めない</strong>——荷主の社名も
     * 割引率も、荷役作業員には要らない。渡せば渡すほど、漏れたときの範囲が広がる。
     *
     * <p>人のロールでも開かない。経理担当者はこの入口ではなく billingms の画面を使う。
     */
    private static final Set<String> TRUSTED_SERVICE_PRINCIPALS = Set.of("system:billingms");

    private final BillableCargoFinder billable;

    public BillingLookupController(BillableCargoFinder billable) {
        this.billable = billable;
    }

    /** 料金算出の対象になる予約を並べる。**経理担当者が仕事を始める相手である。** */
    @GetMapping("/billable")
    public List<BillableCargo> billable(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId) {
        requireTrustedService(userId);
        return billable.findAllBillable();
    }

    /**
     * 料金算出の入力を 1 件引く。
     *
     * <p><strong>料金算出の対象でない予約は 404 である。</strong>まだ運び終えていない予約に
     * 請求書を出すことはできない（[ADR-027] 決定 5）——絞りはポートが持つ。
     */
    @GetMapping("/{bookingId}/billing-snapshot")
    public BillableCargo billingSnapshot(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @PathVariable String bookingId) {
        requireTrustedService(userId);

        return billable.findBillable(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "料金算出の対象になる予約が見つかりません"));
    }

    private void requireTrustedService(String userId) {
        if (!AuthenticatedUser.of(userId, null).isOneOf(TRUSTED_SERVICE_PRINCIPALS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
