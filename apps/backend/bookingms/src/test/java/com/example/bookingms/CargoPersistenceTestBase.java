package com.example.bookingms;


import com.example.bookingms.application.internal.BookCargoCommand;
import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.ShipperType;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 実際の DB で確かめる貨物予約テストの土台。
 *
 * <p>採番・地点の結合・危険物と冷凍の保存は、いずれも DB の振る舞いに依存する。
 * ユニットテストのスタブが緑でも、ここが噛み合わなければ 1 件も登録できない。
 *
 * <p>コンテナと組み立てをここに置くのは、局面ごとにテストを分けても<strong>同じ DB を
 * 同じ形で使う</strong>ためである。写すと、片方だけが古い組み立てのまま残る。
 * Spring の文脈は同じ構成なら使い回されるため、コンテナが 2 つ立つことはない。
 */
@SpringBootTest
@org.springframework.context.annotation.Import(
        CargoPersistenceTestBase.RecordingNotifier.class)
@ActiveProfiles("integration")
abstract class CargoPersistenceTestBase {


    /**
     * DB は 1 つを共有し、止めない。
     *
     * <p>{@code @Container} の静的フィールドは<strong>それを宣言したクラスの終了時に止まる</strong>。
     * 土台を継承するテストが 2 つあると、先に終わったほうが DB を止め、後のほうは接続できない。
     * 起動しっぱなしにして、JVM の終了に任せる（Testcontainers の Ryuk が後片付けする）。
     */
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Autowired
    protected BookCargoUseCase bookCargo;

    @Autowired
    protected SearchCargoUseCase searchCargo;

    @Autowired
    protected RegisterShipperUseCase registerShipper;

    @Autowired
    protected LocationRepository locations;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected CargoRepository repository;

    /** 業務タイムゾーンの時刻源。実装と同じものを使う（別々に「今日」を決めない）。 */
    @Autowired
    protected java.time.Clock clock;

    @Autowired
    protected com.example.bookingms.application.internal.IssueTrackingNumberUseCase
            issueTrackingNumber;

    /** 発行の時点でトランザクションが生きていたか。 */
    protected static boolean transactionActiveWhenPublished;

    /**
     * 発行の呼び出しを捕まえる差し替え。
     *
     * <p>本物（RabbitMQ）に送らせない。ここで見たいのは<strong>いつ呼ばれるか</strong>である。
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class RecordingNotifier {

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.example.bookingms.application.port.CargoEventNotifier recordingCargoEventNotifier() {
            return event -> transactionActiveWhenPublished = org.springframework.transaction
                    .support.TransactionSynchronizationManager.isSynchronizationActive();
        }
    }

    protected Long shipperId(String name, String email) {
        RegistrationOutcome outcome = registerShipper.registerAnyway(new RegisterShipperCommand(
                ShipperType.INDIVIDUAL, name, email, "東京都千代田区 1-1-1", null));
        return ((RegistrationOutcome.Registered) outcome).shipper().id();
    }

    protected BookCargoCommand command(Long shipperId, CargoType type) {
        return new BookCargoCommand(shipperId, type, new BigDecimal("12000"), 20, "電子部品",
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                "JPTYO", "USLAX", LocalDate.of(2030, Month.SEPTEMBER, 1), LocalDate.of(2030, Month.SEPTEMBER, 20),
                type == CargoType.HAZARDOUS ? "3" : null,
                type == CargoType.HAZARDOUS ? "UN1263" : null,
                type == CargoType.HAZARDOUS ? "PAINT" : null,
                type == CargoType.REFRIGERATED ? new BigDecimal("-20") : null,
                type == CargoType.REFRIGERATED ? new BigDecimal("-15") : null);
    }


    protected static final ZoneId LA = ZoneId.of("America/Los_Angeles");

    protected static CargoItinerary itineraryVia(String transitUnLocode, String transitName) {
        return CargoItinerary.of(List.of(
                Leg.of(VoyageNumber.of("V0201"), Location.of("JPTYO", "Tokyo"),
                        Location.of(transitUnLocode, transitName),
                        Instant.parse("2030-09-02T09:00:00Z"),
                        Instant.parse("2030-09-05T09:00:00Z")),
                Leg.of(VoyageNumber.of("V0202"), Location.of(transitUnLocode, transitName),
                        Location.of("USLAX", "Los Angeles"),
                        Instant.parse("2030-09-06T09:00:00Z"),
                        Instant.parse("2030-09-18T09:00:00Z"))));
    }
}
