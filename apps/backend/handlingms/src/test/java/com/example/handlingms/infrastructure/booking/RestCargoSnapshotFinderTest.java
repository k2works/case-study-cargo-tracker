package com.example.handlingms.infrastructure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.handlingms.application.port.CargoLookupUnavailableException;
import com.example.handlingms.domain.model.CargoSnapshot;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import com.example.handlingms.domain.model.HandlingType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.CargoSnapshotContract;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 貨物の照会 ACL（handlingms → bookingms・[ADR-023] 決定 2）。
 *
 * <p><strong>コンシューマ側の契約テスト</strong>である。ここで固定した期待（経路・名乗り・
 * 応答の形）を、プロバイダ側（bookingms）の契約テストが同じ形で満たす。
 * <strong>経路と名乗りは共有の契約から読む</strong>——写しを 2 つ置くと、片方だけ直したことを
 * 誰も検出できない。
 */
@DisplayName("貨物の照会（ACL）")
class RestCargoSnapshotFinderTest {

    private static final HandlingTrackingNumber NUMBER =
            HandlingTrackingNumber.of("TRK-20260823-0001");

    private static final String BASE_URL = "http://bookingms:8080";

    private static final String SNAPSHOT_JSON = """
            {"bookingId": "BKG-2026000001",
             "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
             "legs": [
               {"voyageNumber": "V0100", "loadUnLocode": "JPTYO", "unloadUnLocode": "CNSHA"},
               {"voyageNumber": "V0200", "loadUnLocode": "CNSHA", "unloadUnLocode": "USLAX"}
             ]}
            """;

    private MockRestServiceServer server;
    private RestCargoSnapshotFinder finder;

    private static String expectedUri() {
        return BASE_URL + CargoSnapshotContract.PATH.replace("{trackingNumber}", NUMBER.value());
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        finder = new RestCargoSnapshotFinder(builder.build());
    }

    @Test
    @DisplayName("追跡番号で引き、照合できる形に変換する")
    void findsByTrackingNumber() {
        server.expect(requestTo(expectedUri()))
                .andRespond(withSuccess(SNAPSHOT_JSON, MediaType.APPLICATION_JSON));

        CargoSnapshot snapshot = finder.findByTrackingNumber(NUMBER).orElseThrow();

        assertThat(snapshot.bookingId()).isEqualTo("BKG-2026000001");
        // 変換の結果は、照合が通ることで確かめる。項目を 1 つずつ比べると、
        // 増えた項目の写し忘れに気づけない
        assertThat(snapshot.isOffRoute(HandlingType.RECEIVE, "JPTYO")).isFalse();
        assertThat(snapshot.isOffRoute(HandlingType.UNLOAD, "CNSHA")).isFalse();
        assertThat(snapshot.isOffRoute(HandlingType.CLAIM, "USLAX")).isFalse();
        assertThat(snapshot.isOffRoute(HandlingType.UNLOAD, "SGSIN")).isTrue();
        server.verify();
    }

    /**
     * <strong>システム主体として名乗る</strong>（[ADR-019] 後日談 3）。
     *
     * <p>名乗らないと、相手の [ADR-007] フィルタが一律に断る。荷役を記録しようとした
     * 瞬間にだけ必ず失敗し、IT5 では実環境の往復を通すまで誰も気づかなかった。
     */
    @Test
    @DisplayName("システム主体として名乗る")
    void identifiesItselfAsASystem() {
        server.expect(requestTo(expectedUri()))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER,
                        CargoSnapshotContract.CALLER_PRINCIPAL))
                .andRespond(withSuccess(SNAPSHOT_JSON, MediaType.APPLICATION_JSON));

        finder.findByTrackingNumber(NUMBER);

        server.verify();
    }

    /**
     * 名乗らないと相手が断ることを、こちら側でも固定する。
     *
     * <p>名乗りのヘッダを消しても、上のテストは<strong>期待を書き換えれば緑になる</strong>。
     * 相手が断る形（401）を流して、それが「見つからない」に化けないことを見る。
     */
    @Test
    @DisplayName("名乗りが通らなければ、見つからないではなく確かめられない")
    void doesNotTreatUnauthorizedAsNotFound() {
        server.expect(requestTo(expectedUri()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> finder.findByTrackingNumber(NUMBER))
                .as("401 を「見つかりません」にすると、配線の誤りが番号の誤りに見える")
                .isInstanceOf(CargoLookupUnavailableException.class);
    }

    @Test
    @DisplayName("相手が「無い」と答えたら、無い")
    void treatsNotFoundAsEmpty() {
        server.expect(requestTo(expectedUri())).andRespond(withResourceNotFound());

        Optional<CargoSnapshot> found = finder.findByTrackingNumber(NUMBER);

        assertThat(found).isEmpty();
    }

    /**
     * 「確かめられなかった」と「無かった」を混ぜない。
     *
     * <p>混ぜると、bookingms が落ちているときに荷役作業員へ「その追跡番号は存在しません」と
     * 伝わり、作業員は番号を疑って打ち直し続ける。
     */
    @Test
    @DisplayName("相手が落ちているときは、無いとは答えない")
    void doesNotTreatFailureAsEmpty() {
        server.expect(requestTo(expectedUri())).andRespond(withServerError());

        assertThatThrownBy(() -> finder.findByTrackingNumber(NUMBER))
                .isInstanceOf(CargoLookupUnavailableException.class);
    }

    /**
     * <strong>知らない項目で壊れない。</strong>
     *
     * <p>壊れると、bookingms が項目を 1 つ足しただけで荷役の記録が止まる。
     */
    @Test
    @DisplayName("知らない項目が増えても読める")
    void ignoresUnknownFields() {
        server.expect(requestTo(expectedUri()))
                .andRespond(withSuccess("""
                        {"bookingId": "BKG-2026000001",
                         "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
                         "legs": [],
                         "shipperName": "この項目はまだ知らない"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(finder.findByTrackingNumber(NUMBER)).isPresent();
    }

    /**
     * <strong>受け皿の項目が、合意した契約と一致する。</strong>
     *
     * <p>手書きの名簿は、相手が項目を足しても赤にならない。足した項目をこちらが読めているかは
     * 誰も確かめておらず、実物でだけ null になる。
     */
    @Test
    @DisplayName("受け皿の項目の名簿が、合意した契約と一致する")
    void rosterIsDerivedFromTheDto() {
        assertThat(java.util.Arrays.stream(CargoSnapshotResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList())
                .as("受け皿の項目が変わった。bookingms 側の応答も直すこと")
                .containsExactlyElementsOf(CargoSnapshotContract.FIELDS);

        assertThat(java.util.Arrays.stream(CargoSnapshotResponse.LegResponse.class
                                .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList())
                .containsExactlyElementsOf(CargoSnapshotContract.LEG_FIELDS);
    }
}
