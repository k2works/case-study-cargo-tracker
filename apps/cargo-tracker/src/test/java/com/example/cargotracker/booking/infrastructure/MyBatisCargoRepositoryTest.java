package com.example.cargotracker.booking.infrastructure;

import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.infrastructure.repositories.MyBatisCargoRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MyBatisCargoRepository 統合テスト")
class MyBatisCargoRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MyBatisCargoRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE cargo, shipper RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("Cargo を保存して BookingId で取得できる")
    void shouldSaveAndFindByBookingId() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        insertShipper(shipperId, "SHP-1001");
        Cargo cargo = cargo(shipperId, CargoType.GENERAL, new BigDecimal("10.500"));

        repository.save(cargo);

        Optional<Cargo> actual = repository.findByBookingId(cargo.getBookingId());

        assertThat(actual).isPresent();
        assertThat(actual.get()).usingRecursiveComparison().isEqualTo(cargo);
        assertThat(actual.get().getStatus()).isEqualTo(BookingStatus.PRELIMINARY);
    }

    @Test
    @DisplayName("存在しない BookingId は Optional.empty を返す")
    void shouldReturnEmptyWhenBookingIdDoesNotExist() {
        Optional<Cargo> actual = repository.findByBookingId(new BookingId(UUID.randomUUID()));

        assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("findAll で全件取得できる")
    void shouldFindAll() {
        ShipperId firstShipperId = new ShipperId(UUID.randomUUID());
        ShipperId secondShipperId = new ShipperId(UUID.randomUUID());
        insertShipper(firstShipperId, "SHP-2001");
        insertShipper(secondShipperId, "SHP-2002");
        Cargo first = cargo(firstShipperId, CargoType.GENERAL, new BigDecimal("5.000"));
        Cargo second = cargo(secondShipperId, CargoType.REFRIGERATED, new BigDecimal("8.250"));

        repository.save(first);
        repository.save(second);

        List<Cargo> actual = repository.findAll();

        assertThat(actual)
                .hasSize(2)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("findByShipperId で荷主の予約一覧を取得できる")
    void shouldFindByShipperId() {
        ShipperId targetShipperId = new ShipperId(UUID.randomUUID());
        ShipperId otherShipperId = new ShipperId(UUID.randomUUID());
        insertShipper(targetShipperId, "SHP-3001");
        insertShipper(otherShipperId, "SHP-3002");
        Cargo first = cargo(targetShipperId, CargoType.GENERAL, new BigDecimal("3.000"));
        Cargo second = cargo(targetShipperId, CargoType.HAZARDOUS, new BigDecimal("12.000"));
        Cargo other = cargo(otherShipperId, CargoType.REFRIGERATED, new BigDecimal("1.500"));

        repository.save(first);
        repository.save(second);
        repository.save(other);

        List<Cargo> actual = repository.findByShipperId(targetShipperId);

        assertThat(actual)
                .hasSize(2)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(first, second);
    }

    private Cargo cargo(ShipperId shipperId, CargoType cargoType, BigDecimal weight) {
        return new Cargo(
                new BookingId(UUID.randomUUID()),
                shipperId,
                cargoType,
                weight,
                new RouteSpecification(
                        new Location("JPTYO"),
                        new Location("USLAX"),
                        LocalDate.now().plusDays(14)
                )
        );
    }

    private void insertShipper(ShipperId shipperId, String shipperCode) {
        jdbcTemplate.update(
                """
                INSERT INTO shipper (id, shipper_code, shipper_type, name, email, phone)
                VALUES (CAST(? AS UUID), ?, 'INDIVIDUAL', ?, ?, ?)
                """,
                shipperId.toString(),
                shipperCode,
                "テスト荷主 " + shipperCode,
                shipperCode.toLowerCase() + "@example.com",
                "090-0000-0000"
        );
    }
}
