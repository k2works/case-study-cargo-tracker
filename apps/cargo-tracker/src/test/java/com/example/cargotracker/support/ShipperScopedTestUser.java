package com.example.cargotracker.support;

import com.example.cargotracker.shared.application.security.ShipperScopedPrincipal;
import com.example.cargotracker.shared.domain.model.ShipperId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 紐付けを持つ荷主としてリクエストするための認証情報（US34 / US35）。
 *
 * <p><strong>{@code @WithUserDetails} は使えない。</strong> 認証情報は
 * {@code @BeforeEach} より<strong>前</strong>に組み立てられるため、テストの中で
 * 作った荷主との紐付けが載らない。<strong>{@code @WithMockUser} でも足りない</strong> —
 * {@code UserDetailsService} を通らず、紐付けを持たない素の利用者になる。
 *
 * <p>本番と同じ形（共有の約束を実装した認証情報）をその場で作る。
 * <strong>Security Context のクラスは参照しない</strong> — 参照すると BC をまたぐ
 * （ArchUnit ルール 4）。
 *
 * <p><strong>2 つ目のテストが同じものを必要としたので、ここに 1 つだけ置く。</strong>
 * 別々に持つと、紐付けの読み方が変わったときに片方だけが直る。
 */
public final class ShipperScopedTestUser extends User implements ShipperScopedPrincipal {

    private static final long serialVersionUID = 1L;

    private final UUID shipperId;

    private ShipperScopedTestUser(UUID shipperId) {
        super("shipper", "password", List.of(new SimpleGrantedAuthority("ROLE_SHIPPER")));
        this.shipperId = shipperId;
    }

    /** 指定した荷主に紐付いた利用者としてリクエストする。 */
    public static RequestPostProcessor scopedTo(UUID shipperId) {
        return SecurityMockMvcRequestPostProcessors.user(new ShipperScopedTestUser(shipperId));
    }

    @Override
    public Optional<ShipperId> linkedShipperId() {
        return Optional.of(new ShipperId(shipperId));
    }

    /** 利用者 ID で同一性を判断する（本番の {@code ShipperScopedUser} と同じ）。 */
    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
