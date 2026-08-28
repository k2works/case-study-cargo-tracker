package com.example.bookingms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.internal.queryservices.SearchShipperUseCase;
import com.example.bookingms.domain.repository.ShipperRepository;
import com.example.bookingms.domain.model.valueobjects.ContractNumber;
import com.example.bookingms.domain.model.valueobjects.CorporateContract;
import com.example.bookingms.domain.model.valueobjects.DiscountRate;
import com.example.bookingms.domain.model.valueobjects.EmailAddress;
import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.model.valueobjects.ShipperProfile;
import com.example.bookingms.domain.model.valueobjects.ShipperType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.example.bookingms.domain.model.commands.RegisterShipperCommand;

@DisplayName("荷主の登録")
class RegisterShipperUseCaseTest {

    private final List<Shipper> stored = new ArrayList<>();

    private final ShipperRepository repository = new ShipperRepository() {
        @Override
        public Optional<Shipper> findByEmail(EmailAddress email) {
            return stored.stream().filter(s -> email.equals(s.email())).findFirst();
        }

        @Override
        public Optional<Shipper> findById(Long id) {
            return stored.stream().filter(shipper -> id.equals(shipper.id())).findFirst();
        }

        /**
         * 採番だけを行い、渡された内容はそのまま保つ。
         *
         * <p>ここで一部の項目（契約情報など）を捨てると、ユースケースがその項目を渡し忘れても
         * 結果が同じになり、検査が本番の誤りを判別しなくなる。偽物の保存先ほど、本物と同じだけ
         * 受け取ったものを返す必要がある。
         */
        @Override
        public Shipper save(Shipper shipper) {
            Shipper saved = Shipper.restore(
                    (long) (stored.size() + 1),
                    "SHP-%06d".formatted(stored.size() + 1),
                    shipper.type(),
                    new ShipperProfile(shipper.name(), shipper.email(), shipper.address(), shipper.phone()),
                    shipper.contract().orElse(null));
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
            RegistrationOutcome outcome = useCase.register(command("山田太郎", "yamada@example.com"));

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
            useCase.register(command("山田太郎", "yamada@example.com"));

            RegistrationOutcome outcome =
                    useCase.register(command("山田太郎（別入力）", "yamada@example.com"));

            assertThat(outcome).isInstanceOf(RegistrationOutcome.DuplicateFound.class);
            Shipper existing = ((RegistrationOutcome.DuplicateFound) outcome).existing();
            assertThat(existing.name()).isEqualTo("山田太郎");
            assertThat(stored).hasSize(1);
        }

        @Test
        @DisplayName("それでも新規で登録すると選んだ場合は別の荷主として登録する")
        void registersAnywayWhenChosen() {
            useCase.register(command("山田太郎", "yamada@example.com"));

            RegistrationOutcome outcome =
                    useCase.registerAnyway(command("山田太郎（本社）", "yamada@example.com"));

            assertThat(outcome).isInstanceOf(RegistrationOutcome.Registered.class);
            assertThat(stored).hasSize(2);
            // 同姓同名・同一メールの別部署のような実態があるため、選択の結果は尊重する
            assertThat(stored.get(1).shipperCode()).isNotEqualTo(stored.get(0).shipperCode());
        }
    }

    @Nested
    @DisplayName("法人の契約情報")
    class WithCorporateContract {

        /**
         * 契約情報が登録の結果まで届くことを確かめる。
         *
         * <p>US22（法人割引）は契約番号と割引率がここで保たれていることを前提にする。
         * 登録の入口で落ちても、荷主が作られること自体は成功するため、この検査が無いと
         * 「割引が効かない」という形で US22 のときに初めて分かる。
         */
        @Test
        @DisplayName("契約番号と割引率が登録の結果に残る")
        void keepsContractOnRegistration() {
            RegisterShipperCommand command = new RegisterShipperCommand(
                    ShipperType.CORPORATE, "丸紅商事株式会社", "info@marubeni.example.com",
                    "東京都", "03-0000-0000",
                    new CorporateContract(ContractNumber.of("CT-0001"), DiscountRate.ofPercent(new BigDecimal("15"))));

            useCase.register(command);

            Shipper saved = stored.get(0);
            assertThat(saved.contractNumber()).map(ContractNumber::value).contains("CT-0001");
            assertThat(saved.discountRate()).map(DiscountRate::rate)
                    .contains(new BigDecimal("0.1500"));
        }
    }

    @Nested
    @DisplayName("検索")
    class Search {

        @Test
        @DisplayName("登録済みの荷主を探せる")
        void findsRegisteredShippers() {
            useCase.register(command("山田太郎", "yamada@example.com"));

            assertThat(new SearchShipperUseCase(repository).search("山田")).hasSize(1);
        }
    }
}
