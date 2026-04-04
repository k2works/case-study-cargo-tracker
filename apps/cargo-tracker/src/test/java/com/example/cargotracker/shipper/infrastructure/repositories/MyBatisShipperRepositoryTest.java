package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
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
        jdbcTemplate.execute("TRUNCATE TABLE shipper");
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
        CorporateShipper shipper = new CorporateShipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-2001"),
                new ShipperName("テスト商事株式会社"),
                new Email("sales@test-corp.example.com"),
                new Phone("03-1234-5678"),
                new ContractNumber("CN-001"),
                new DiscountRate(new BigDecimal("0.10"))
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
                ShipperType.INDIVIDUAL
        );
        Shipper second = new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-3002"),
                new ShipperName("田中 次郎"),
                new Email("duplicated@example.com"),
                new Phone("080-3333-4444"),
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
                ShipperType.INDIVIDUAL
        );
        CorporateShipper corporate = new CorporateShipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-4002"),
                new ShipperName("法人 荷主株式会社"),
                new Email("corporate@example.com"),
                new Phone("03-9999-8888"),
                new ContractNumber("CN-002"),
                new DiscountRate(new BigDecimal("0.05"))
        );

        repository.save(individual);
        repository.save(corporate);

        List<Shipper> actual = repository.findAll();

        assertThat(actual)
                .hasSize(2)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(individual, corporate);
    }
}
