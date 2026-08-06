package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MyBatisShipperRepository 統合テスト")
class MyBatisShipperRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MyBatisShipperRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE cargo, shipper RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("個人荷主を保存して ID で取得できる")
    void shouldSaveAndFindIndividualShipperById() {
        Shipper shipper = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-1001"),
                new ShipperName("山田 太郎"),
                new Email("taro.yamada@example.com"),
                new Phone("090-1111-2222"),
                null,
                ShipperType.INDIVIDUAL
        );

        repository.save(shipper);

        Optional<Shipper> actual = repository.findById(shipper.getId());

        assertThat(actual).isPresent();
        assertThat(actual.get()).usingRecursiveComparison().isEqualTo(shipper);
    }

    @Test
    @DisplayName("法人荷主を保存してメールで取得できる")
    void shouldSaveAndFindCorporateShipperByEmail() {
        CorporateShipper shipper = corporateShipper(
                "SHP-2001",
                "テスト商事株式会社",
                "sales@test-corp.example.com",
                "03-1234-5678",
                "CN-001",
                "0.10"
        );

        repository.save(shipper);

        Optional<Shipper> actual = repository.findByEmail(shipper.getEmail());

        assertThat(actual).isPresent();
        assertThat(actual.get()).isInstanceOf(CorporateShipper.class);
        assertThat(actual.get()).usingRecursiveComparison().isEqualTo(shipper);
    }

    @Test
    @DisplayName("同じメールアドレスで 2 件保存すると制約違反になる")
    void shouldThrowWhenSavingDuplicateEmail() {
        Shipper first = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-3001"),
                new ShipperName("佐藤 花子"),
                new Email("duplicated@example.com"),
                new Phone("080-1111-2222"),
                null,
                ShipperType.INDIVIDUAL
        );
        Shipper second = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-3002"),
                new ShipperName("田中 次郎"),
                new Email("duplicated@example.com"),
                new Phone("080-3333-4444"),
                null,
                ShipperType.INDIVIDUAL
        );

        repository.save(first);

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("存在しない ID は Optional.empty を返す")
    void shouldReturnEmptyWhenIdDoesNotExist() {
        Optional<Shipper> actual = repository.findById(new ShipperId(UUID.randomUUID()));

        assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("findAll で全件取得できる")
    void shouldFindAllShippers() {
        Shipper individual = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-4001"),
                new ShipperName("個人 荷主"),
                new Email("individual@example.com"),
                new Phone("090-5555-6666"),
                null,
                ShipperType.INDIVIDUAL
        );
        CorporateShipper corporate = corporateShipper(
                "SHP-4002",
                "法人 荷主株式会社",
                "corporate@example.com",
                "03-9999-8888",
                "CN-002",
                "0.05"
        );

        repository.save(individual);
        repository.save(corporate);

        List<Shipper> actual = repository.findAll();

        assertThat(actual)
                .hasSize(2)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(individual, corporate);
    }

    @Test
    @DisplayName("Address 付きの荷主を保存して取得できる")
    void shouldSaveAndFindShipperWithAddress() {
        Shipper shipper = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-5001"),
                new ShipperName("住所付き 荷主"),
                new Email("address@example.com"),
                new Phone("090-7777-8888"),
                new com.example.cargotracker.shipper.domain.model.valueobjects.Address("東京都千代田区1-1-1"),
                ShipperType.INDIVIDUAL
        );

        repository.save(shipper);

        Optional<Shipper> actual = repository.findById(shipper.getId());
        assertThat(actual).isPresent();
        assertThat(actual.get().getAddress()).isNotNull();
        assertThat(actual.get().getAddress().value()).isEqualTo("東京都千代田区1-1-1");
    }

    @Test
    @DisplayName("Phone が null の荷主を保存して取得できる")
    void shouldSaveAndFindShipperWithoutPhone() {
        Shipper shipper = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-5002"),
                new ShipperName("電話なし 荷主"),
                new Email("nophone@example.com"),
                null,
                null,
                ShipperType.INDIVIDUAL
        );

        repository.save(shipper);

        Optional<Shipper> actual = repository.findById(shipper.getId());
        assertThat(actual).isPresent();
        assertThat(actual.get().getPhone()).isNull();
    }

    @Test
    @DisplayName("ShipperCode で荷主を検索できる")
    void shouldFindByShipperCode() {
        Shipper shipper = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-5003"),
                new ShipperName("コード検索テスト"),
                new Email("codetest@example.com"),
                new Phone("090-5555-5555"),
                null,
                ShipperType.INDIVIDUAL
        );

        repository.save(shipper);

        Optional<Shipper> actual = repository.findByCode(shipper.getCode());
        assertThat(actual).isPresent();
        assertThat(actual.get().getCode().value()).isEqualTo("SHP-5003");
    }

    private CorporateShipper corporateShipper(
            String code,
            String name,
            String email,
            String phone,
            String contractNumber,
            String discountRate
    ) {
        Shipper baseShipper = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode(code),
                new ShipperName(name),
                new Email(email),
                new Phone(phone),
                null,
                ShipperType.CORPORATE
        );
        return new CorporateShipper(
                baseShipper,
                new ContractNumber(contractNumber),
                new DiscountRate(new BigDecimal(discountRate))
        );
    }
}
