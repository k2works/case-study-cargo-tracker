package com.example.cargotracker.shared.infrastructure.web;

import java.util.Collection;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;

/**
 * ダッシュボードに作業入口（カード）を持つロール（IT13 レビュー C16）。
 *
 * <p><strong>「利用できる機能はありません」を出してよいのは、カードが 1 枚も
 * 出ていないときだけである。</strong> 画面の中でロールを列挙していたため、
 * カードを足すたびに列挙を書き足し忘れ、<strong>入口があるのに「ありません」と
 * 出る</strong>状態が 2 度起きた（US34 の荷主、IT13 の経理担当者）。
 *
 * <p><strong>ここが唯一の名簿である。</strong> ロール別のカードを足したら
 * この集合にも足す。忘れると {@code NavigationConsistencyTest} が赤になる
 * — テンプレートの {@code sec:authorize} に現れるロールが
 * この集合に含まれているかを機械が突き合わせる。
 */
public final class DashboardEntryRoles {

    /** 作業入口を持つロール（{@code ROLE_} 接頭辞なし）。 */
    public static final Set<String> ROLES = Set.of(
            "SALES", "ROUTER", "TRACKER", "HANDLER", "ADMIN",
            "SHIPPER", "CONSIGNEE", "BILLING");

    private DashboardEntryRoles() {
    }

    /**
     * 作業入口を持つロールを 1 つでも持っているか。
     *
     * @param authorities 認証情報の権限（{@code ROLE_} 接頭辞つき）
     */
    public static boolean hasEntry(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return false;
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .anyMatch(ROLES::contains);
    }
}
