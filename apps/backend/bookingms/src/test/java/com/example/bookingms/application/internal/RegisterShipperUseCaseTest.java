package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.Shipper;
import com.example.bookingms.domain.model.ShipperType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("荷主の登録")
class RegisterShipperUseCaseTest {

    private final List<Shipper> stored = new ArrayList<>();

    private final ShipperRepository repository = new ShipperRepository() {
        @Override
        public Optional<Shipper> findByEmail(String email) {
            return stored.stream().filter(s -> s.email().equals(email)).findFirst();
        }

        @Override
        public Shipper save(Shipper shipper) {
            Shipper saved = Shipper.restore(
                    (long) (stored.size() + 1),
                    "SHP-%06d".formatted(stored.size() + 1),
                    shipper.type(), shipper.name(), shipper.email(), shipper.address(), shipper.phone());
            stored.add(saved);
            return saved;
        }

        @Override
        public List<Shipper> search(String keyword) {
            return List.copyOf(stored);
        }
    };

    private final RegisterShipperUseCase useCase = new RegisterShipperUseCase(repository);

    private RegisterShipperCommand command(String name, String email) {
        return new RegisterShipperCommand(ShipperType.INDIVIDUAL, name, email, "東京都", "03-0000-0000");
    }

    @Nested
    @DisplayName("重複がない場合")
    class WithoutDuplicate {

        @Test
        @DisplayName("登録して荷主 ID を発行する")
        void registers() {
            RegistrationOutcome outcome = useCase.register(command("山田太郎", "yamada@example.com"), false);

            assertThat(outcome).isInstanceOf(RegistrationOutcome.Registered.class);
            Shipper registered = ((RegistrationOutcome.Registered) outcome).shipper();
            assertThat(registered.shipperCode()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("同じメールアドレスが既にある場合")
    class WithDuplicate {

        @Test
        @DisplayName("登録せず既存の荷主を提示する")
        void presentsExistingShipper() {
            useCase.register(command("山田太郎", "yamada@example.com"), false);

            RegistrationOutcome outcome =
                    useCase.register(command("山田太郎（別入力）", "yamada@example.com"), false);

            assertThat(outcome).isInstanceOf(RegistrationOutcome.DuplicateFound.class);
            Shipper existing = ((RegistrationOutcome.DuplicateFound) outcome).existing();
            assertThat(existing.name()).isEqualTo("山田太郎");
            assertThat(stored).hasSize(1);
        }

        @Test
        @DisplayName("それでも新規で登録すると選んだ場合は別の荷主として登録する")
        void registersAnywayWhenChosen() {
            useCase.register(command("山田太郎", "yamada@example.com"), false);

            RegistrationOutcome outcome =
                    useCase.register(command("山田太郎（本社）", "yamada@example.com"), true);

            assertThat(outcome).isInstanceOf(RegistrationOutcome.Registered.class);
            assertThat(stored).hasSize(2);
            // 同姓同名・同一メールの別部署のような実態があるため、選択の結果は尊重する
            assertThat(stored.get(1).shipperCode()).isNotEqualTo(stored.get(0).shipperCode());
        }
    }

    @Nested
    @DisplayName("検索")
    class Search {

        @Test
        @DisplayName("登録済みの荷主を探せる")
        void findsRegisteredShippers() {
            useCase.register(command("山田太郎", "yamada@example.com"), false);

            assertThat(useCase.search("山田")).hasSize(1);
        }
    }
}
