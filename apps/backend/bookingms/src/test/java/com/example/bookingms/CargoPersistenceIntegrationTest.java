package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bookingms.application.internal.BookCargoCommand;
import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.ShipperType;
import com.example.bookingms.domain.model.TransportStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 貨物予約が実際の DB で成立することを確認する。
 *
 * <p>採番・地点の結合・危険物と冷凍の保存は、いずれも DB の振る舞いに依存する。
 * ユニットテストのスタブが緑でも、ここが噛み合わなければ 1 件も登録できない。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("貨物予約の永続化")
class CargoPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private BookCargoUseCase bookCargo;

    @Autowired
    private SearchCargoUseCase searchCargo;

    @Autowired
    private RegisterShipperUseCase registerShipper;

    @Autowired
    private LocationRepository locations;

    private Long shipperId(String name, String email) {
        RegistrationOutcome outcome = registerShipper.registerAnyway(new RegisterShipperCommand(
                ShipperType.INDIVIDUAL, name, email, "東京都千代田区 1-1-1", null));
        return ((RegistrationOutcome.Registered) outcome).shipper().id();
    }

    private BookCargoCommand command(Long shipperId, CargoType type) {
        return new BookCargoCommand(shipperId, type, new BigDecimal("12000"), 20, "電子部品",
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                "JPTYO", "USLAX", LocalDate.of(2030, Month.SEPTEMBER, 1), LocalDate.of(2030, Month.SEPTEMBER, 20),
                type == CargoType.HAZARDOUS ? "Class 3" : null,
                type == CargoType.HAZARDOUS ? "UN1263" : null,
                type == CargoType.HAZARDOUS ? "PAINT" : null,
                type == CargoType.REFRIGERATED ? new BigDecimal("-20") : null,
                type == CargoType.REFRIGERATED ? new BigDecimal("-15") : null);
    }

    @Test
    @DisplayName("予約番号が本番経路（DB シーケンス）で採番される")
    void assignsBookingIdFromDatabase() {
        Cargo booked = bookCargo.book(command(shipperId("採番太郎", "cargo-seq@example.com"),
                CargoType.GENERAL));

        assertThat(booked.bookingId())
                .as("予約番号が採番されていない。5 サービスが参照するキーが空になる")
                .isPresent();
        // 形式そのものが契約になる（ADR-011）
        assertThat(booked.bookingId().orElseThrow().value()).matches("^BKG-\\d{10}$");
    }

    @Test
    @DisplayName("連続して登録しても予約番号が衝突しない")
    void assignsDistinctBookingIds() {
        Long shipperId = shipperId("連番太郎", "cargo-seq2@example.com");

        BookingId first = bookCargo.book(command(shipperId, CargoType.GENERAL))
                .bookingId().orElseThrow();
        BookingId second = bookCargo.book(command(shipperId, CargoType.GENERAL))
                .bookingId().orElseThrow();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("仮受付として保存され、状態が空欄にならない")
    void persistsPreliminaryStatuses() {
        Cargo booked = bookCargo.book(command(shipperId("状態太郎", "cargo-status@example.com"),
                CargoType.GENERAL));

        assertThat(booked.bookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
        assertThat(booked.transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
        assertThat(booked.routingStatus()).isEqualTo(RoutingStatus.NOT_ROUTED);
    }

    @Test
    @DisplayName("地点は名称まで読み戻せる（画面がコードから引き直さずに済む）")
    void restoresLocationNames() {
        Cargo booked = bookCargo.book(command(shipperId("地点太郎", "cargo-loc@example.com"),
                CargoType.GENERAL));

        assertThat(booked.routeSpecification().origin().name()).isEqualTo("Tokyo");
        assertThat(booked.routeSpecification().destination().name()).isEqualTo("Los Angeles");
    }

    @Test
    @DisplayName("危険物の申告が保存され、経路設計が読める形になっている")
    void persistsHazardousDeclaration() {
        // IT3 の経路設計はこの値を入力にする。保存されていなければ、対応可能な航海を選べない
        Long shipperId = shipperId("危険物太郎", "cargo-haz@example.com");
        Cargo booked = bookCargo.book(command(shipperId, CargoType.HAZARDOUS));

        Cargo reloaded = searchCargo.search(CargoType.HAZARDOUS, null).cargoes().stream()
                .filter(c -> c.id().equals(booked.id()))
                .findFirst()
                .orElseThrow();

        assertThat(reloaded.requiresHazardousDeclaration()).isTrue();
        assertThat(reloaded.hazardousDeclaration().orElseThrow().unNumber()).isEqualTo("UN1263");
        assertThat(reloaded.hazardousDeclaration().orElseThrow().hazardousClass())
                .isEqualTo("Class 3");
        assertThat(reloaded.hazardousDeclaration().orElseThrow().properShippingName())
                .isEqualTo("PAINT");
    }

    @Test
    @DisplayName("冷凍の温度条件が保存され、下限と上限が入れ替わらない")
    void persistsTemperatureRequirement() {
        Long shipperId = shipperId("冷凍太郎", "cargo-ref@example.com");
        Cargo booked = bookCargo.book(command(shipperId, CargoType.REFRIGERATED));

        Cargo reloaded = searchCargo.search(CargoType.REFRIGERATED, null).cargoes().stream()
                .filter(c -> c.id().equals(booked.id()))
                .findFirst()
                .orElseThrow();

        assertThat(reloaded.temperatureRequirement().orElseThrow().minCelsius())
                .isEqualByComparingTo("-20");
        assertThat(reloaded.temperatureRequirement().orElseThrow().maxCelsius())
                .isEqualByComparingTo("-15");
    }

    @Test
    @DisplayName("存在しない荷主 ID の予約は拒否する")
    void rejectsUnknownShipper() {
        // 通すと、誰の貨物か分からない予約が保存される
        assertThatThrownBy(() -> bookCargo.book(command(999_999L, CargoType.GENERAL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷主");
    }

    @Test
    @DisplayName("存在しない地点コードの予約は拒否する")
    void rejectsUnknownLocation() {
        Long shipperId = shipperId("地点不明", "cargo-noloc@example.com");
        BookCargoCommand unknown = new BookCargoCommand(shipperId, CargoType.GENERAL,
                new BigDecimal("100"), null, null, null, null, null,
                "JPTYO", "ZZZZZ", null, LocalDate.of(2030, Month.SEPTEMBER, 20),
                null, null, null, null, null);

        assertThatThrownBy(() -> bookCargo.book(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    @DisplayName("一覧は新しい順で、種別で絞り込める")
    void listsNewestFirstAndFiltersByType() {
        Long shipperId = shipperId("一覧太郎", "cargo-list@example.com");
        bookCargo.book(command(shipperId, CargoType.GENERAL));
        Cargo latest = bookCargo.book(command(shipperId, CargoType.GENERAL));

        List<Cargo> all = searchCargo.search(null, null).cargoes();

        // 登録順だと、今入れた 1 件が常に最下部に沈む
        assertThat(all.get(0).id()).isEqualTo(latest.id());
        assertThat(searchCargo.search(CargoType.GENERAL, null).cargoes())
                .allMatch(cargo -> cargo.type() == CargoType.GENERAL);
    }

    @Test
    @DisplayName("予約番号と荷主名で絞り込める")
    void filtersByKeyword() {
        Long shipperId = shipperId("絞込花子", "cargo-keyword@example.com");
        Cargo booked = bookCargo.book(command(shipperId, CargoType.GENERAL));
        String bookingId = booked.bookingId().orElseThrow().value();

        assertThat(searchCargo.search(null, "絞込花子").cargoes())
                .extracting(cargo -> cargo.id())
                .contains(booked.id());
        assertThat(searchCargo.search(null, bookingId).cargoes()).hasSize(1);
    }

    @Test
    @DisplayName("地点マスタは業務タイムゾーンを持つ")
    void locationsCarryBusinessTimeZone() {
        // 到着期限を目的地の暦で判断するのに要る（ADR-010）
        assertThat(locations.timeZoneOf("USLAX")).contains(ZoneId.of("America/Los_Angeles"));
        assertThat(locations.timeZoneOf("JPTYO")).contains(ZoneId.of("Asia/Tokyo"));
        assertThat(locations.findAll()).isNotEmpty();
    }
}
