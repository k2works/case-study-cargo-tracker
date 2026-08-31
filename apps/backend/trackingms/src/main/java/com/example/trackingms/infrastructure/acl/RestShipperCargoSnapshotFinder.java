package com.example.trackingms.infrastructure.acl;

import com.example.shared.auth.AuthenticatedUser;
import com.example.trackingms.application.internal.queryservices.ShipperCargoSnapshot;
import com.example.trackingms.application.internal.outboundservices.acl.ShipperCargoSnapshotFinder;
import com.example.trackingms.application.internal.outboundservices.acl.ShipperTrackingLookupUnavailableException;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** bookingms から荷主境界の判定に要る Snapshot を引く ACL。 */
public class RestShipperCargoSnapshotFinder implements ShipperCargoSnapshotFinder {

    public static final String SYSTEM_PRINCIPAL = "system:trackingms";
    /**
     * bookingms の内部 API の経路。
     *
     * <p><strong>定数で持つ。</strong>理由は {@link RestUserShipperLinkFinder#PATH} と同じで、
     * 経路は契約テストが両側で突き合わせる。設定で差し替えられるようにはしない。
     */
    @SuppressWarnings("java:S1075")
    public static final String PATH = "/api/v1/bookings/shipper-snapshots/{trackingNumber}";

    /**
     * 荷主の貨物 Snapshot をまとめて引く経路。
     *
     * <p>理由は {@link #PATH} と同じ。契約テストが両側で突き合わせる。
     */
    @SuppressWarnings("java:S1075")
    public static final String BY_SHIPPER_PATH = "/api/v1/bookings/shipper-snapshots";

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

    @Override
    public List<ShipperCargoSnapshot> findByShipperId(long shipperId) {
        ShipperCargoSnapshotResponse[] response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path(BY_SHIPPER_PATH)
                            .queryParam("shipperId", shipperId)
                            .build())
                    .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                    .retrieve()
                    .body(ShipperCargoSnapshotResponse[].class);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
        if (response == null) {
            return List.of();
        }
        return java.util.Arrays.stream(response)
                .map(RestShipperCargoSnapshotFinder::toDomain)
                .toList();
    }

    @Override
    public java.util.Set<String> simulatedAmong(List<TrackingNumber> trackingNumbers) {
        if (trackingNumbers.isEmpty()) {
            // 空で問うと、絞り込みの無い一覧を引いてしまう入口になりうる。
            return java.util.Set.of();
        }
        List<String> values = trackingNumbers.stream().map(TrackingNumber::value).toList();
        ShipperCargoSnapshotResponse[] response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path(BY_SHIPPER_PATH)
                            .queryParam("trackingNumbers", values)
                            .build())
                    .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                    .retrieve()
                    .body(ShipperCargoSnapshotResponse[].class);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
        if (response == null) {
            return java.util.Set.of();
        }
        return java.util.Arrays.stream(response)
                .filter(ShipperCargoSnapshotResponse::simulated)
                .map(ShipperCargoSnapshotResponse::trackingNumber)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static ShipperCargoSnapshot toDomain(ShipperCargoSnapshotResponse response) {
        return new ShipperCargoSnapshot(response.bookingId(), response.trackingNumber(),
                response.shipperId(), response.simulated());
    }

    private static ShipperTrackingLookupUnavailableException unavailable(Exception cause) {
        return new ShipperTrackingLookupUnavailableException(
                "貨物の荷主を確認できませんでした。しばらくしてからもう一度お試しください", cause);
    }

    /**
     * 契約の項目。{@code simulated} は<strong>由来がシミュレーションか</strong>
     * （[ADR-030] 決定 3）。荷主コードそのものは返さない——追跡側に荷主の採番規則を
     * 知らせる必要はなく、知らせると規則を変えたときに両方を直すことになる。
     */
    record ShipperCargoSnapshotResponse(String bookingId, String trackingNumber, Long shipperId,
            boolean simulated) {
    }
}
