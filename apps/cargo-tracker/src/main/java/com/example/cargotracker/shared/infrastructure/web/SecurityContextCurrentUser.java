package com.example.cargotracker.shared.infrastructure.web;

import com.example.cargotracker.shared.application.security.CurrentUser;
import com.example.cargotracker.shared.application.security.ShipperScopedPrincipal;
import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spring Security の認証情報から利用者を読む（US34）。
 *
 * <p><strong>principal の型に依存しない。</strong> {@link ShipperScopedPrincipal} を
 * 実装していない相手（テストの `@WithMockUser` など）では空を返す。
 */
@Component
public class SecurityContextCurrentUser implements CurrentUser {

    /** 荷主として絞り込む対象のロール。 */
    private static final String SHIPPER_ROLE = "ROLE_SHIPPER";

    @Override
    public Optional<ShipperId> linkedShipperId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ShipperScopedPrincipal scoped)) {
            return Optional.empty();
        }
        return scoped.linkedShipperId();
    }

    /**
     * 荷主ロールなら絞り込む。
     *
     * <p><strong>紐付けの有無では決めない。</strong> 紐付けを忘れた荷主に
     * 全社の予約が見える形を作らないためである。
     */
    @Override
    public boolean scopedToShipper() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> SHIPPER_ROLE.equals(a.getAuthority()));
    }
}
