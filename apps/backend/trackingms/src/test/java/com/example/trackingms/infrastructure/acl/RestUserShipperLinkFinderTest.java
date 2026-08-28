package com.example.trackingms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.UserShipperLinkContract;
import com.example.trackingms.application.port.ShipperTrackingLookupUnavailableException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("利用者と荷主の紐付け照会 ACL")
class RestUserShipperLinkFinderTest {

    private static final String BASE = "http://authms:8080";

    private MockRestServiceServer server;
    private RestUserShipperLinkFinder finder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        finder = new RestUserShipperLinkFinder(builder.build());
    }

    @Test
    @DisplayName("authms の契約で定義された経路と主体を使う")
    void usesUserShipperLinkContract() {
        assertThat(RestUserShipperLinkFinder.PATH).isEqualTo(UserShipperLinkContract.PATH);
        assertThat(RestUserShipperLinkFinder.SYSTEM_PRINCIPAL)
                .isEqualTo(UserShipperLinkContract.TRACKING_CALLER_PRINCIPAL);
    }

    @Test
    @DisplayName("利用者名で荷主 ID を引く")
    void findsLinkedShipperId() {
        server.expect(requestTo(BASE + pathFor("shipper01")))
                .andRespond(withSuccess("""
                        {"linked": true, "shipperId": 1}
                        """, MediaType.APPLICATION_JSON));

        Optional<Long> found = finder.findLinkedShipperId("shipper01");

        assertThat(found).contains(1L);
        server.verify();
    }

    @Test
    @DisplayName("システム主体として名乗る")
    void identifiesItself() {
        server.expect(requestTo(BASE + pathFor("shipper01")))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER,
                        UserShipperLinkContract.TRACKING_CALLER_PRINCIPAL))
                .andRespond(withSuccess("""
                        {"linked": true, "shipperId": 1}
                        """, MediaType.APPLICATION_JSON));

        finder.findLinkedShipperId("shipper01");

        server.verify();
    }

    @Test
    @DisplayName("紐付けなしは空で返す")
    void returnsEmptyWhenUnlinked() {
        server.expect(requestTo(BASE + pathFor("shipper01")))
                .andRespond(withSuccess("""
                        {"linked": false, "shipperId": null}
                        """, MediaType.APPLICATION_JSON));

        assertThat(finder.findLinkedShipperId("shipper01")).isEmpty();
    }

    @Test
    @DisplayName("認可されなければ、紐付けなしにはしない")
    void doesNotTreatForbiddenAsUnlinked() {
        server.expect(requestTo(BASE + pathFor("shipper01")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> finder.findLinkedShipperId("shipper01"))
                .isInstanceOf(ShipperTrackingLookupUnavailableException.class);
    }

    @Test
    @DisplayName("authms が落ちているときは、紐付けなしにはしない")
    void doesNotTreatFailureAsUnlinked() {
        server.expect(requestTo(BASE + pathFor("shipper01")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> finder.findLinkedShipperId("shipper01"))
                .isInstanceOf(ShipperTrackingLookupUnavailableException.class);
    }

    private static String pathFor(String username) {
        return UserShipperLinkContract.PATH.replace("{username}", username);
    }
}
