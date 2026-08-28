package com.example.authms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.LoginState;
import com.example.authms.domain.model.User;
import com.example.authms.domain.model.UserIdentity;
import com.example.authms.domain.model.UserShipperLink;
import com.example.shared.auth.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("利用者と荷主の紐付け管理")
class ManageUserShipperLinkUseCaseTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final ManageUserShipperLinkUseCase useCase = new ManageUserShipperLinkUseCase(users);

    @Test
    @DisplayName("荷主ロールの利用者を荷主 ID に紐付ける")
    void linksShipperUser() {
        users.found = Optional.of(user("shipper01", Role.ROLE_SHIPPER));

        UserShipperLink linked = useCase.link("shipper01", 1L).orElseThrow();

        assertThat(linked.username()).isEqualTo("shipper01");
        assertThat(linked.shipperId()).isEqualTo(1L);
        assertThat(users.saved).containsExactly(linked);
    }

    @Test
    @DisplayName("存在しない利用者は空で返す")
    void returnsEmptyWhenUserMissing() {
        users.found = Optional.empty();

        assertThat(useCase.link("missing", 1L)).isEmpty();
        assertThat(users.saved).isEmpty();
    }

    @Test
    @DisplayName("荷主ロールでない利用者は紐付けない")
    void rejectsNonShipperUser() {
        users.found = Optional.of(user("sales01", Role.ROLE_SALES));

        assertThatThrownBy(() -> useCase.link("sales01", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷主ロール");
        assertThat(users.saved).isEmpty();
    }

    @Test
    @DisplayName("紐付けを解除する")
    void unlinksUser() {
        users.found = Optional.of(user("shipper01", Role.ROLE_SHIPPER));

        Optional<UserShipperLink> removed = useCase.unlink("shipper01");

        assertThat(removed).contains(new UserShipperLink("shipper01", 1L));
        assertThat(users.unlinked).containsExactly("shipper01");
    }

    private static User user(String username, Role role) {
        return User.restore(1L,
                new UserIdentity(username, username + "@example.com", username, "hash"),
                true, new LoginState(0, null), Set.of(role));
    }

    private static final class InMemoryUsers implements UserRepository {
        Optional<User> found = Optional.empty();
        List<UserShipperLink> saved = new ArrayList<>();
        List<String> unlinked = new ArrayList<>();

        @Override
        public Optional<User> findByUsername(String username) {
            return found;
        }

        @Override
        public void updateLoginState(User user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User recordFailedAttempt(User user, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<User> findLocked(Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> findLinkedShipperId(String username) {
            return "shipper01".equals(username) ? Optional.of(1L) : Optional.empty();
        }

        @Override
        public UserShipperLink saveShipperLink(UserShipperLink link) {
            saved.add(link);
            return link;
        }

        @Override
        public Optional<UserShipperLink> removeShipperLink(String username) {
            unlinked.add(username);
            return findLinkedShipperId(username).map(shipperId -> new UserShipperLink(username, shipperId));
        }
    }
}
