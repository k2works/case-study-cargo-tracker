package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.infrastructure.projection.CargoProjection;
import com.example.cargotracker.booking.infrastructure.projection.ShipperProjection;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 荷主向けの予約（S45・S46 / 引き継ぎ 2）。
 *
 * <p><b>荷主で絞るのはサーバである。</b> 画面が捨てる形にすると、応答には
 * 他社の予約が乗ったままになり、開発者用の道具で読める。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperBookingControllerIT extends AbstractAxonIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ShipperProjection shippers;

    @Autowired
    private CargoProjection cargos;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String registerShipper() {
        String shipperId = "SHP-B-" + System.nanoTime();
        shippers.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL", "自社商事",
                shipperId + "@example.com", null, null, null, null, false));
        return shipperId;
    }

    private String bookCargo(String shipperId, String productName) {
        String bookingId = "B-SB-" + System.nanoTime();
        cargos.on(new CargoBookedEvent(bookingId, shipperId, "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                10, productName, null, null, null, null, "sales01"));
        return bookingId;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> get(String path, String shipperId) {
        var request = rest.get().uri("http://localhost:" + port + path);
        if (shipperId != null) {
            request = request.header("X-Auth-Shipper-Id", shipperId);
        }
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>)
                request.retrieve().toEntity(Map.class);
    }

    @Test
    @DisplayName("S45: 自社の予約だけが一覧に出る（他社の予約は応答に乗らない）")
    @SuppressWarnings("unchecked")
    void listsOnlyOwnBookings() {
        String own = registerShipper();
        String other = registerShipper();
        String ownBooking = bookCargo(own, "自社の貨物");
        String otherBooking = bookCargo(other, "他社の貨物");

        var response = get("/api/v1/booking/shipper/bookings?limit=200", own);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var items = (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).extracting(item -> item.get("bookingId"))
                .contains(ownBooking)
                .doesNotContain(otherBooking);
        assertThat(items).allSatisfy(item -> assertThat(item)
                .as("**金額・社内メモ・担当者名は出さない**（ui_design.md S45）")
                .doesNotContainKeys("updatedBy", "returnReason", "conditionReviewReason",
                        "shipperName"));
    }

    @Test
    @DisplayName("S46: 他社の予約は、予約 ID を知っていても開けない")
    void hidesOtherShippersProgress() {
        String own = registerShipper();
        String other = registerShipper();
        String otherBooking = bookCargo(other, "他社の貨物");

        assertThat(get("/api/v1/booking/shipper/bookings/" + otherBooking, own)
                .getStatusCode())
                .as("**存在しないものと区別しない**——区別すると総当たりで在ることが分かる")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("S46: 自社の予約は進み具合を開ける")
    @SuppressWarnings("unchecked")
    void showsOwnProgress() {
        String own = registerShipper();
        String bookingId = bookCargo(own, "自社の貨物");

        var response = get("/api/v1/booking/shipper/bookings/" + bookingId, own);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("bookingId", bookingId)
                .containsKeys("bookingStatus", "legs", "notifications")
                .doesNotContainKeys("updatedBy", "shipperName", "returnReason");
    }

    @Test
    @DisplayName("紐付けの無い利用者には返さない（全件に倒さない）")
    void refusesWithoutTheShipperLink() {
        assertThat(get("/api/v1/booking/shipper/bookings", null).getStatusCode())
                .as("ヘッダを落とすだけで他社の予約が見える形にしない")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
