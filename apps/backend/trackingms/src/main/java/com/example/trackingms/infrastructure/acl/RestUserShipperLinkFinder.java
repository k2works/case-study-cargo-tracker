package com.example.trackingms.infrastructure.acl;

import com.example.shared.auth.AuthenticatedUser;
import com.example.trackingms.application.port.ShipperTrackingLookupUnavailableException;
import com.example.trackingms.application.port.UserShipperLinkFinder;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** authms から利用者と荷主の紐付けを引く ACL。 */
public class RestUserShipperLinkFinder implements UserShipperLinkFinder {

    public static final String SYSTEM_PRINCIPAL = "system:trackingms";
    public static final String PATH = "/api/v1/internal/user-shipper-links/{username}";

    private final RestClient restClient;

    public RestUserShipperLinkFinder(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<Long> findLinkedShipperId(String username) {
        UserShipperLinkResponse response;
        try {
            response = restClient.get()
                    .uri(PATH, username)
                    .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                    .retrieve()
                    .body(UserShipperLinkResponse.class);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
        if (response == null || !response.linked()) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.shipperId());
    }

    private static ShipperTrackingLookupUnavailableException unavailable(Exception cause) {
        return new ShipperTrackingLookupUnavailableException(
                "荷主の紐付けを確認できませんでした。しばらくしてからもう一度お試しください", cause);
    }

    record UserShipperLinkResponse(boolean linked, Long shipperId) {
    }
}
