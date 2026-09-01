package com.example.handlingms.infrastructure.acl;

import com.example.handlingms.application.internal.outboundservices.acl.CargoLookupUnavailableException;
import com.example.handlingms.application.internal.outboundservices.acl.CargoSnapshotFinder;
import com.example.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.handlingms.domain.model.valueobjects.HandlingTrackingNumber;
import com.example.handlingms.domain.model.valueobjects.LegSnapshot;
import com.example.shared.auth.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 追跡番号から貨物を bookingms へ取りに行く ACL（[ADR-023] 決定 2）。
 *
 * <p>bookingms の型はここから先へ出さない。{@link CargoSnapshotResponse} で受け、
 * Handling Context の {@link CargoSnapshot} へ変換する。
 *
 * <p><strong>システム主体として名乗る</strong>（[ADR-019] 後日談 3）。相手の [ADR-007]
 * フィルタは利用者ヘッダの無い要求を一律に断るため、何も付けないと
 * <strong>荷役を記録しようとした瞬間にだけ必ず 401 になる</strong>。IT5 では名乗りを忘れ、
 * 実環境の往復を通すまで誰も気づかなかった。
 *
 * <p>名乗るのは呼び出し元の利用者ではなく、システム自身である。利用者ヘッダを伝播すると、
 * bookingms 側の認可が「荷役作業員が予約を見てよいか」を見ることになり、
 * <strong>荷役作業員に予約の閲覧権限を与える</strong>ことになる。
 */
public class RestCargoSnapshotFinder implements CargoSnapshotFinder {

    /**
     * このサービス自身を表す主体。
     *
     * <p>利用者 ID と取り違えられない形にする。利用者と同じ見た目にすると、監査ログで
     * 「誰がやったのか」が分からなくなる。
     */
    public static final String SYSTEM_PRINCIPAL = "system:handlingms";

    private final RestClient restClient;

    public RestCargoSnapshotFinder(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<CargoSnapshot> findByTrackingNumber(HandlingTrackingNumber trackingNumber) {
        CargoSnapshotResponse response;
        try {
            // catch は呼び出しだけを囲む。変換まで囲むと、こちら側の不具合まで
            // 「確かめられません」に化けて原因が消える
            response = restClient.get()
                    .uri("/api/v1/bookings/by-tracking-number/{trackingNumber}",
                            trackingNumber.value())
                    .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                    .retrieve()
                    .body(CargoSnapshotResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // 相手が「無い」と答えたのだから、無い
                return Optional.empty();
            }
            throw unavailable(e);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
        return Optional.ofNullable(response).map(RestCargoSnapshotFinder::toDomain);
    }

    /**
     * 「確かめられなかった」と「無かった」を混ぜない。
     *
     * <p>混ぜると、bookingms が落ちているときに荷役作業員へ「その追跡番号は存在しません」と
     * 伝わり、作業員は番号を疑って打ち直し続ける。
     */
    private static CargoLookupUnavailableException unavailable(Exception cause) {
        return new CargoLookupUnavailableException(
                "貨物を確認できませんでした。しばらくしてからもう一度お試しください", cause);
    }

    private static CargoSnapshot toDomain(CargoSnapshotResponse response) {
        List<LegSnapshot> legs = response.legs().stream()
                .map(leg -> new LegSnapshot(leg.voyageNumber(), leg.loadUnLocode(),
                        leg.unloadUnLocode()))
                .toList();
        // **送られてこなければ実業務として扱う。**Boolean.TRUE.equals は null に強い
        return CargoSnapshot.of(response.bookingId(), response.originUnLocode(),
                response.destinationUnLocode(), legs, Boolean.TRUE.equals(response.simulated()));
    }
}
