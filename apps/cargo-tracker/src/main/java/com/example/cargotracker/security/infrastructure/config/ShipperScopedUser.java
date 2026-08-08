package com.example.cargotracker.security.infrastructure.config;

import com.example.cargotracker.shared.application.security.ShipperScopedPrincipal;
import com.example.cargotracker.shared.domain.model.ShipperId;
import java.util.Collection;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * 荷主に紐づく利用者（US34）。
 *
 * <p><strong>Spring Security の {@code User} に紐付けを 1 つ足しただけのものである。</strong>
 * 認証の仕組みそのものは変えない。
 *
 * <p>Booking Context はこの型を知らない。読むのは
 * {@link ShipperScopedPrincipal}（共有）だけであり、**BC をまたがない**。
 */
public class ShipperScopedUser extends User implements ShipperScopedPrincipal {

    private static final long serialVersionUID = 1L;

    /**
     * 紐づく荷主の識別子。
     *
     * <p><strong>{@code UUID} で持ち、{@code transient} にしない。</strong>
     * 認証情報はセッションと一緒に直列化されうる（セッション複製・保存）。
     * {@code transient} にすると復元時に紐付けが消え、
     * <strong>復元された荷主に 1 件も見えなくなる</strong>。
     */
    private final java.util.UUID linkedShipperId;

    public ShipperScopedUser(
            String username, String password, boolean enabled, boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities, ShipperId linkedShipperId) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.linkedShipperId = linkedShipperId == null ? null : linkedShipperId.value();
    }

    @Override
    public Optional<ShipperId> linkedShipperId() {
        return Optional.ofNullable(linkedShipperId).map(ShipperId::new);
    }

    /**
     * <strong>利用者 ID で同一性を判断する</strong>（Spring の {@code User} と同じ）。
     *
     * <p>紐づく荷主は利用者の属性であり、**同じ利用者 ID で違う荷主に紐づくことはない**。
     * 明示的に上書きしているのは、親クラスの規則に暗黙に乗ると
     * 「フィールドを足したのに同一性の規則を考えていない」形になるためである。
     */
    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
