package com.example.trackingms.infrastructure.acl;

import com.example.shared.auth.AuthenticatedUser;
import com.example.trackingms.application.internal.queryservices.ShipperCargoSnapshot;
import com.example.trackingms.application.internal.outboundservices.acl.ShipperCargoSnapshotFinder;
import com.example.trackingms.application.internal.outboundservices.acl.ShipperTrackingLookupUnavailableException;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** bookingms から荷主境界の判定に要る Snapshot を引く ACL。 */
public class RestShipperCargoSnapshotFinder implements ShipperCargoSnapshotFinder {

    public static final String SYSTEM_PRINCIPAL = "system:trackingms";
    public static final String PATH = "/api/v1/bookings/shipper-snapshots/{trackingNumber}";

    private final RestClient restClient;

    public RestShipperCargoSnapshotFinder(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<ShipperCargoSnapshot> findByTrackingNumber(TrackingNumber trackingNumber) {
        ShipperCargoSnapshotResponse response;
        try {
            response = restClient.get()
                    .uri(PATH, trackingNumber.value())
                    .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                    .retrieve()
                    .body(ShipperCargoSnapshotResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw unavailable(e);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
        return Optional.ofNullable(response).map(RestShipperCargoSnapshotFinder::toDomain);
    }

    private static ShipperCargoSnapshot toDomain(ShipperCargoSnapshotResponse response) {
        return new ShipperCargoSnapshot(response.bookingId(), response.trackingNumber(),
                response.shipperId());
    }

    private static ShipperTrackingLookupUnavailableException unavailable(Exception cause) {
        return new ShipperTrackingLookupUnavailableException(
                "貨物の荷主を確認できませんでした。しばらくしてからもう一度お試しください", cause);
    }

    record ShipperCargoSnapshotResponse(String bookingId, String trackingNumber, Long shipperId) {
    }
}
