package com.example.trackingms.infrastructure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.shared.auth.AuthenticatedUser;
import com.example.trackingms.application.internal.ShipperCargoSnapshot;
import com.example.trackingms.application.port.ShipperTrackingLookupUnavailableException;
import com.example.trackingms.domain.model.TrackingNumber;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("荷主貨物 Snapshot 照会 ACL")
class RestShipperCargoSnapshotFinderTest {

    private static final String BASE = "http://bookingms:8080";
    private static final TrackingNumber NUMBER = TrackingNumber.of("TRK-20260823-0001");

    private MockRestServiceServer server;
    private RestShipperCargoSnapshotFinder finder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        finder = new RestShipperCargoSnapshotFinder(builder.build());
    }

    @Test
    @DisplayName("追跡番号で荷主境界の Snapshot を引く")
    void findsSnapshot() {
        server.expect(requestTo(BASE + "/api/v1/bookings/shipper-snapshots/" + NUMBER.value()))
                .andRespond(withSuccess("""
                        {"bookingId": "BKG-2026000001",
                         "trackingNumber": "TRK-20260823-0001",
                         "shipperId": 1}
                        """, MediaType.APPLICATION_JSON));

        ShipperCargoSnapshot snapshot = finder.findByTrackingNumber(NUMBER).orElseThrow();

        assertThat(snapshot.shipperId()).isEqualTo(1L);
        assertThat(snapshot.trackingNumber()).isEqualTo(NUMBER.value());
        server.verify();
    }

    @Test
    @DisplayName("システム主体として名乗る")
    void identifiesItself() {
        server.expect(requestTo(BASE + "/api/v1/bookings/shipper-snapshots/" + NUMBER.value()))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER, "system:trackingms"))
                .andRespond(withSuccess("""
                        {"bookingId": "BKG-2026000001",
                         "trackingNumber": "TRK-20260823-0001",
                         "shipperId": 1}
                        """, MediaType.APPLICATION_JSON));

        finder.findByTrackingNumber(NUMBER);

        server.verify();
    }

    @Test
    @DisplayName("bookingms が無いと答えたら空にする")
    void treatsNotFoundAsEmpty() {
        server.expect(requestTo(BASE + "/api/v1/bookings/shipper-snapshots/" + NUMBER.value()))
                .andRespond(withResourceNotFound());

        Optional<ShipperCargoSnapshot> found = finder.findByTrackingNumber(NUMBER);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("認可されなければ、無いことにはしない")
    void doesNotTreatForbiddenAsEmpty() {
        server.expect(requestTo(BASE + "/api/v1/bookings/shipper-snapshots/" + NUMBER.value()))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> finder.findByTrackingNumber(NUMBER))
                .isInstanceOf(ShipperTrackingLookupUnavailableException.class);
    }

    @Test
    @DisplayName("bookingms が落ちているときは、無いことにはしない")
    void doesNotTreatFailureAsEmpty() {
        server.expect(requestTo(BASE + "/api/v1/bookings/shipper-snapshots/" + NUMBER.value()))
                .andRespond(withServerError());

        assertThatThrownBy(() -> finder.findByTrackingNumber(NUMBER))
                .isInstanceOf(ShipperTrackingLookupUnavailableException.class);
    }
}
