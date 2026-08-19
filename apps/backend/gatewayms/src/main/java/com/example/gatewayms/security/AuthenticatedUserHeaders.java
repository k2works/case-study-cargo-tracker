package com.example.gatewayms.security;

/**
 * Gateway が検証済みのクレームを下流サービスへ渡すヘッダ名（ADR-004）。
 *
 * <p>各サービスは署名を再検証せずこのヘッダだけを見る。したがって Gateway は、
 * 利用者が自分で名乗ったこれらのヘッダを必ず剥がしてから自分の値を付ける。
 */
public final class AuthenticatedUserHeaders {

    public static final String USER_ID = "X-Authenticated-User-Id";
    public static final String ROLES = "X-Authenticated-Roles";

    private AuthenticatedUserHeaders() {
    }
}
