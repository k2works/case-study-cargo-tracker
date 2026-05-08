package com.example.bookingms.domain.model.aggregates;

import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.Leg;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cargo 集約ルートのドメインテスト
 */
@DisplayName("Cargo ドメインテスト")
class CargoTest {

    private Cargo cargo;
    private CargoItinerary itinerary;

    @BeforeEach
    void setUp() {
        BookingId bookingId = new BookingId("TESTBOOKING1");
        RouteSpecification spec = new RouteSpecification("JPOSA", "USLAX", LocalDate.of(2026, 6, 30));
        cargo = new Cargo(bookingId, 1L, CargoType.GENERAL, new Weight(BigDecimal.valueOf(100)), spec);

        Leg leg = new Leg("V0042", "JPOSA", "USLAX",
                LocalDateTime.of(2026, 4, 1, 18, 0),
                LocalDateTime.of(2026, 4, 14, 8, 0));
        itinerary = new CargoItinerary(List.of(leg));
    }

    @Test
    @DisplayName("新規作成時の初期ステータスは PRELIMINARY であること")
    void shouldHaveInitialStatusPreliminary() {
        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
    }

    @Test
    @DisplayName("assignRoute を呼ぶと旅程が設定されステータスが ROUTE_PROPOSED に遷移すること")
    void shouldAssignRouteAndChangeStatusToRouteProposed() {
        cargo.assignRoute(itinerary);

        assertThat(cargo.getCargoItinerary()).isEqualTo(itinerary);
        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
    }

    @Test
    @DisplayName("assignRoute に null を渡すと例外が発生すること")
    void shouldThrowExceptionWhenAssignNullItinerary() {
        assertThatThrownBy(() -> cargo.assignRoute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("confirm を呼ぶとステータスが CONFIRMED に遷移すること")
    void shouldConfirmAndChangeStatusToConfirmed() {
        cargo.assignRoute(itinerary);
        cargo.confirm();

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("ROUTE_PROPOSED でない状態から confirm を呼ぶと例外が発生すること")
    void shouldThrowExceptionWhenConfirmingNotRouteProposedCargo() {
        assertThatThrownBy(() -> cargo.confirm())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancel を呼ぶとステータスが CANCELLED に遷移すること")
    void shouldCancelAndChangeStatusToCancelled() {
        cargo.cancel();

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("DELIVERED 状態から cancel を呼ぶと例外が発生すること")
    void shouldThrowExceptionWhenCancellingDeliveredCargo() {
        // DELIVERED 状態を直接設定するため再構成コンストラクタを使用
        Cargo delivered = new Cargo(1L, new BookingId("TESTDELIVER1"), 1L,
                BookingStatus.DELIVERED, CargoType.GENERAL,
                new Weight(BigDecimal.valueOf(100)),
                new RouteSpecification("JPOSA", "USLAX", LocalDate.of(2026, 6, 30)));

        assertThatThrownBy(delivered::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    @DisplayName("SETTLED 状態から cancel を呼ぶと例外が発生すること")
    void shouldThrowExceptionWhenCancellingSettledCargo() {
        Cargo settled = new Cargo(2L, new BookingId("TESTSETTLED1"), 1L,
                BookingStatus.SETTLED, CargoType.GENERAL,
                new Weight(BigDecimal.valueOf(100)),
                new RouteSpecification("JPOSA", "USLAX", LocalDate.of(2026, 6, 30)));

        assertThatThrownBy(settled::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SETTLED");
    }

    @Test
    @DisplayName("specifyRoute で経路仕様を更新できること")
    void shouldUpdateRouteSpecification() {
        RouteSpecification newSpecification =
                new RouteSpecification("NLRTM", "USNYC", LocalDate.of(2026, 7, 31));

        cargo.specifyRoute(newSpecification);

        assertThat(cargo.getRouteSpecification()).isEqualTo(newSpecification);
    }

    @Test
    @DisplayName("同じ bookingId の Cargo は等価であること")
    void shouldCompareByBookingId() {
        Cargo sameBookingId = new Cargo(
                new BookingId("TESTBOOKING1"),
                99L,
                CargoType.HAZARDOUS,
                new Weight(BigDecimal.valueOf(200)),
                null
        );

        assertThat(cargo)
                .isEqualTo(sameBookingId)
                .hasSameHashCodeAs(sameBookingId);
        assertThat(cargo.toString()).contains("TESTBOOKING1");
    }

    @Test
    @DisplayName("異なる bookingId や別型とは等価でないこと")
    void shouldNotBeEqualWhenBookingIdDiffersOrTypeDiffers() {
        Cargo differentBookingId = new Cargo(
                new BookingId("DIFFERENT001"),
                1L,
                CargoType.GENERAL,
                new Weight(BigDecimal.valueOf(100)),
                null
        );

        assertThat(cargo)
                .isNotEqualTo(differentBookingId)
                .isNotEqualTo("cargo");
    }
}
