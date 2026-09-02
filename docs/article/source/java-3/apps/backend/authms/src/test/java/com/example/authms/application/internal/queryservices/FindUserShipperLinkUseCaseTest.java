package com.example.authms.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 利用者と荷主の紐付け照会（US33）。 */
@DisplayName("利用者と荷主の紐付け照会")
class FindUserShipperLinkUseCaseTest {

    private Optional<Long> linkedShipperId = Optional.empty();

    private final UserRepository users = new UserRepository() {
        @Override
        public Optional<User> findByUsername(String username) {
            throw new UnsupportedOperationException("このテストでは使わない");
        }

        @Override
        public void updateLoginState(User user) {
            throw new UnsupportedOperationException("このテストでは使わない");
        }

        @Override
        public User recordFailedAttempt(User user, Instant now) {
            throw new UnsupportedOperationException("このテストでは使わない");
        }

        @Override
        public List<User> findLocked(Instant now) {
            throw new UnsupportedOperationException("このテストでは使わない");
        }

        @Override
        public Optional<Long> findLinkedShipperId(String username) {
            return linkedShipperId;
        }
    };

    private final FindUserShipperLinkUseCase useCase = new FindUserShipperLinkUseCase(users);

    @Test
    @DisplayName("紐付けがあれば linked=true と荷主 ID を返す")
    void returnsLinkedShipperId() {
        linkedShipperId = Optional.of(1L);

        assertThat(useCase.find("shipper01"))
                .isEqualTo(UserShipperLinkResult.linked(1L));
    }

    @Test
    @DisplayName("紐付けが無ければ linked=false を返す")
    void returnsUnlinked() {
        assertThat(useCase.find("sales01"))
                .isEqualTo(UserShipperLinkResult.unlinked());
    }
}
