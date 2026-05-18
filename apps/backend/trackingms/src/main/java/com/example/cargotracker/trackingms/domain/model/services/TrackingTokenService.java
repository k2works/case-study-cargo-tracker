package com.example.cargotracker.trackingms.domain.model.services;

import com.example.cargotracker.trackingms.domain.model.valueobjects.JwtToken;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;

/**
 * 追跡照会用 JWT トークンの発行・検証を担うドメインサービス（ADR-0013）。
 *
 * <p>実装は {@link com.example.cargotracker.trackingms.infrastructure.security.JwtTrackingTokenService}。
 * インフラ層に jjwt 依存を閉じ込めるため interface をドメイン層に置く。</p>
 */
public interface TrackingTokenService {

    /**
     * 追跡番号に紐付く JWT トークンを発行する。
     *
     * @param trackingNumber 追跡番号
     * @param deliveredAt    配送完了日時。{@code null} の場合は exp = 発行時 + 30 日のみで判定。
     *                       非 null の場合は実効有効期限を {@code min(exp, deliveredAt + grace)} へ短縮する。
     * @return 発行された JWT トークン
     */
    JwtToken issue(TrackingNumber trackingNumber, LocalDateTime deliveredAt);

    /**
     * JWT トークンを検証して追跡番号を取り出す。
     *
     * @param token       JWT 文字列
     * @param deliveredAt 配送完了日時（任意）。非 null の場合は配送完了 + grace 経過時刻も判定する。
     * @return 検証成功時の追跡番号
     * @throws InvalidTrackingTokenException 署名不正・改ざん・形式不正
     * @throws TrackingTokenExpiredException exp 経過、または delivered_at + grace 経過
     */
    TrackingNumber verify(String token, LocalDateTime deliveredAt);
}
