package com.example.billingms.infrastructure.booking;

import com.example.billingms.application.port.BillableCargoSnapshot;
import com.example.billingms.application.port.BillingSnapshotFinder;
import com.example.shared.auth.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 料金算出の入力を bookingms へ取りに行く ACL（[ADR-027] 決定 7）。
 *
 * <p>{@code RestRouteCandidateFinder}（bookingms → routingms）と<strong>同じ形</strong>に
 * する——終盤で新しい結合方式を発明しない（開発戦略）。
 *
 * <p><strong>bookingms の型はここから先へ出さない。</strong>{@link BillingSnapshotResponse}
 * で受け、Billing Context の {@link BillableCargoSnapshot} へ変換する。直接
 * デシリアライズすると、相手のドメインの変更がこちらのコンパイルを壊す。
 *
 * <p><strong>利用者ヘッダは伝播せず、システムとして名乗る。</strong>この呼び出しは
 * 「システムが料金の入力を引く」ものであり、利用者の代理ではない。ただし名乗らないと
 * 相手の [ADR-007] フィルタが一律に断る——IT5 では名乗りを忘れ、実環境の往復を通すまで
 * 誰も気づかなかった。
 */
// **経路は設定にしない。**相手との契約であり、環境ごとに変わるものではない
// （変わるのは所在＝ベース URL だけで、そちらは設定から受け取る）。契約テストが
// 両側の経路を突き合わせており、片方だけ直せば赤になる
@SuppressWarnings("java:S1075")
public class RestBillingSnapshotFinder implements BillingSnapshotFinder {

    /**
     * このサービス自身を表す主体。
     *
     * <p>利用者 ID と取り違えられない形にする。利用者と同じ見た目にすると、監査ログで
     * 「誰がやったのか」が分からなくなる。
     */
    public static final String SYSTEM_PRINCIPAL = "system:billingms";

    /**
     * 1 件を引く経路。
     *
     * <p><strong>定数で持つ。</strong>本番のコードは契約フィクスチャ（テスト側）を
     * 読めないため、経路そのものを共有できない。かわりに<strong>両側を突き合わせる
     * 検査</strong>を契約テストに置く——どちらかを直したら赤になる。
     */
    public static final String SNAPSHOT_PATH = "/api/v1/bookings/{bookingId}/billing-snapshot";

    /** 対象の一覧を引く経路。 */
    public static final String BILLABLE_PATH = "/api/v1/bookings/billable";

    private final RestClient restClient;

    public RestBillingSnapshotFinder(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<BillableCargoSnapshot> findBillable(String bookingId) {
        BillingSnapshotResponse response;
        try {
            // catch は呼び出しだけを囲む。変換まで囲むと、こちら側の不具合まで
            // 「見つかりません」に化けて原因が消える
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SNAPSHOT_PATH)
                            .build(bookingId))
                    .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                    .retrieve()
                    .body(BillingSnapshotResponse.class);
        } catch (HttpClientErrorException error) {
            if (error.getStatusCode() == HttpStatus.NOT_FOUND) {
                // **料金算出の対象でない**は正常な結果である。例外にしない
                return Optional.empty();
            }
            throw error;
        }
        return Optional.ofNullable(response).map(BillingSnapshotResponse::toSnapshot);
    }

    @Override
    public List<BillableCargoSnapshot> findAllBillable() {
        BillingSnapshotResponse[] responses = restClient.get()
                .uri(BILLABLE_PATH)
                .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                .retrieve()
                .body(BillingSnapshotResponse[].class);

        return responses == null ? List.of()
                : java.util.Arrays.stream(responses)
                        .map(BillingSnapshotResponse::toSnapshot)
                        .toList();
    }
}
