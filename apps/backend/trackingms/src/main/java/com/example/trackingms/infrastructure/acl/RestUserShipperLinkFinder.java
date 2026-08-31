package com.example.trackingms.infrastructure.acl;

import com.example.shared.auth.AuthenticatedUser;
import com.example.trackingms.application.internal.outboundservices.acl.ShipperTrackingLookupUnavailableException;
import com.example.trackingms.application.internal.outboundservices.acl.UserShipperLinkFinder;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** authms から利用者と荷主の紐付けを引く ACL。 */
public class RestUserShipperLinkFinder implements UserShipperLinkFinder {

    public static final String SYSTEM_PRINCIPAL = "system:trackingms";

    /**
     * authms の内部 API の経路。
     *
     * <p><strong>定数で持つ。</strong>本番のコードは契約フィクスチャ（テスト側）を読めないため、
     * 経路そのものを共有できない。かわりに<strong>両側を突き合わせる検査</strong>を契約テストに
     * 置く——どちらかを直したら赤になる。設定で差し替えられるようにすると、その検査を通り抜けた
     * まま配備先だけが契約からずれる。{@code RestBillingSnapshotFinder} と同じ扱いである。
     */
    @SuppressWarnings("java:S1075")
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
