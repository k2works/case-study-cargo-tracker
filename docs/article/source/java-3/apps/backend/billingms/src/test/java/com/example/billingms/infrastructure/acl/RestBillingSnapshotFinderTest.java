package com.example.billingms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.billingms.application.internal.outboundservices.acl.BillableCargoSnapshot;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.BillingSnapshotContract;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 料金算出の入力の ACL（billingms → bookingms・[ADR-027] 決定 7）。
 *
 * <p><strong>コンシューマ側の契約テスト</strong>である。ここで固定した期待（URL・名乗り・
 * 応答の形）を、プロバイダ側（bookingms の {@code BillingLookupControllerTest}）が同じ形で
 * 満たす。
 */
@DisplayName("料金算出の入力の取得（ACL）")
class RestBillingSnapshotFinderTest {

    /** 相手の所在。**経路は定数から組む**——写すと、経路を直したときに片方だけ残る。 */
    private static final String BASE = "http://booking.test";

    private MockRestServiceServer server;
    private RestBillingSnapshotFinder finder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        finder = new RestBillingSnapshotFinder(builder.build());
    }

    private static final String SNAPSHOT_JSON = """
            {
              "bookingId": "BKG-2026000007",
              "bookingStatus": "DELIVERED",
              "shipperId": "1",
              "shipperName": "丸紅商事株式会社",
              "shipperType": "CORPORATE",
              "discountRate": 0.1000,
              "weightKg": 4200,
              "cargoType": "GENERAL",
              "originName": "Tokyo",
              "destinationName": "Los Angeles",
              "legCount": 2,
              "claimedAt": "2027-09-26T00:00:00Z",
              "misroute": {
                "at": "2027-09-09T00:00:00Z",
                "locationUnLocode": "SGSIN",
                "locationName": "Singapore"
              }
            }
            """;

    /**
     * <strong>契約の経路を呼ぶ。</strong>
     *
     * <p>写して持つと、片方だけ直したことを誰も検出できない。
     */
    @Test
    @DisplayName("契約の経路を呼び、こちらの言葉へ変換する")
    void callsTheContractPathAndTranslates() {
        server.expect(requestTo(
                        BASE + RestBillingSnapshotFinder.SNAPSHOT_PATH.replace("{bookingId}", "BKG-2026000007")))
                .andRespond(withSuccess(SNAPSHOT_JSON, MediaType.APPLICATION_JSON));

        BillableCargoSnapshot snapshot = finder.findBillable("BKG-2026000007").orElseThrow();

        assertThat(snapshot.bookingId()).isEqualTo("BKG-2026000007");
        assertThat(snapshot.corporate()).isTrue();
        assertThat(snapshot.legCount()).isEqualTo(2);
        assertThat(snapshot.misroute().locationName()).isEqualTo("Singapore");
        server.verify();
    }

    /**
     * <strong>システムとして名乗る</strong>（[ADR-007]・[ADR-019] 後日談 3）。
     *
     * <p>名乗らないと相手のフィルタが一律に断る——IT5 では名乗りを忘れ、実環境の往復を
     * 通すまで誰も気づかなかった。
     */
    @Test
    @DisplayName("システムとして名乗り、利用者ヘッダは伝播しない")
    void identifiesItselfWithoutPropagatingTheUser() {
        server.expect(requestTo(
                        BASE + RestBillingSnapshotFinder.SNAPSHOT_PATH.replace("{bookingId}", "BKG-2026000007")))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER,
                        BillingSnapshotContract.CALLER_PRINCIPAL))
                // **ロールは付けない。** 利用者の代理ではない
                .andExpect(headerDoesNotExist(AuthenticatedUser.ROLES_HEADER))
                .andRespond(withSuccess(SNAPSHOT_JSON, MediaType.APPLICATION_JSON));

        finder.findBillable("BKG-2026000007");

        server.verify();
    }

    /**
     * <strong>「対象でない」は正常な結果である。</strong>
     *
     * <p>404 を例外にすると、引取が終わっていない予約を開いただけで
     * <strong>障害として扱われる</strong>。
     */
    @Test
    @DisplayName("料金算出の対象でなければ、空を返す")
    void returnsEmptyWhenTheCargoCannotBeBilled() {
        server.expect(requestTo(
                        BASE + RestBillingSnapshotFinder.SNAPSHOT_PATH.replace("{bookingId}", "BKG-2026000001")))
                .andRespond(withResourceNotFound());

        assertThat(finder.findBillable("BKG-2026000001")).isEmpty();
        server.verify();
    }

    /**
     * <strong>相手の障害は隠さない。</strong>
     *
     * <p>500 を空に倒すと、bookingms が落ちているときに<strong>「料金算出の対象が
     * 1 件もありません」</strong>と表示される——経理担当者は仕事が無いと受け取る。
     */
    @Test
    @DisplayName("相手の障害は空に倒さない")
    void doesNotSwallowServerErrors() {
        server.expect(requestTo(
                        BASE + RestBillingSnapshotFinder.SNAPSHOT_PATH.replace("{bookingId}", "BKG-2026000007")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> finder.findBillable("BKG-2026000007"))
                .as("相手の障害を空に倒している。仕事が無いように見える")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("対象になる予約を並べて受け取る")
    void listsBillableCargoes() {
        server.expect(requestTo(BASE + RestBillingSnapshotFinder.BILLABLE_PATH))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER,
                        BillingSnapshotContract.CALLER_PRINCIPAL))
                .andRespond(withSuccess("[" + SNAPSHOT_JSON + "]",
                        MediaType.APPLICATION_JSON));

        List<BillableCargoSnapshot> all = finder.findAllBillable();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).shipperName()).isEqualTo("丸紅商事株式会社");
        server.verify();
    }

    /** 相手が空を返しても落ちない。 */
    @Test
    @DisplayName("対象が 1 件も無くても落ちない")
    void toleratesAnEmptyList() {
        server.expect(requestTo(BASE + RestBillingSnapshotFinder.BILLABLE_PATH))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(finder.findAllBillable()).isEmpty();
    }
}
