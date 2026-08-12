package com.example.cargotracker.shared.application.security;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import java.util.Optional;

/**
 * 荷主に紐づく利用者（US34）。
 *
 * <p><strong>BC をまたがずに「今ログインしているのは誰の荷主か」を伝えるための約束である。</strong>
 * Security Context が実装し、Booking Context が読む。どちらも相手のクラスを参照せず、
 * 共有カーネルの {@link ShipperId} だけを介する（ADR-005）。
 *
 * <p><strong>空を「全部見える」と読まない。</strong> 荷主ロールで紐付けが無い場合、
 * 見える予約は 0 件が正しい。**設定漏れが情報漏洩に直結する形を作らない。**
 */
public interface ShipperScopedPrincipal {

    /** 紐づく荷主。社内利用者（営業・経路設計者など）では空。 */
    Optional<ShipperId> linkedShipperId();
}
