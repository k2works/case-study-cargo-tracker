package com.example.billingms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.billingms.application.port.BillableCargoSnapshot;
import com.example.shared.contract.BillingSnapshotContract;
import tools.jackson.databind.json.JsonMapper;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 料金算出の入力の契約（[ADR-027] 決定 7）。
 *
 * <p><strong>両側が同じ 1 つを読む。</strong>写しを 2 つ置くと、片方だけ直したことを
 * 誰も検出できない（IT7 返済枠 0.12 と同じ形）。
 *
 * <p><strong>本番の変換器を通した JSON の形まで固定する</strong>（[ADR-022] 決定 5 と
 * 同じ考え方）。手で組み立てた JSON で確かめると、実際の変換器の設定
 * （日時の書き方・命名戦略）がずれていても緑になる。
 */
@DisplayName("料金算出の入力の契約")
class BillingSnapshotContractTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    org.springframework.boot.autoconfigure.AutoConfigurations.of(
                            JacksonAutoConfiguration.class));

    /**
     * <strong>受け取る型が契約の項目をすべて持つ。</strong>
     *
     * <p>取りこぼすと、その項目は<strong>黙って捨てられる</strong>——料金の入力が
     * 欠けたまま金額が出る。
     */
    @Test
    @DisplayName("応答の型が、契約の項目をすべて持つ")
    void theResponseCarriesEveryContractField() {
        List<String> fields = Arrays.stream(BillingSnapshotResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fields)
                .as("契約の項目を受け取る型が持っていない。その項目は黙って捨てられる")
                .containsAll(BillingSnapshotContract.FIELDS);
    }

    @Test
    @DisplayName("誤配とキャンセルの型が、契約の項目をすべて持つ")
    void theNestedTypesCarryEveryContractField() {
        assertThat(Arrays.stream(
                BillingSnapshotResponse.MisrouteResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .containsAll(BillingSnapshotContract.MISROUTE_FIELDS);
        assertThat(Arrays.stream(
                BillingSnapshotResponse.CancellationResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .containsAll(BillingSnapshotContract.CANCELLATION_FIELDS);
    }

    /**
     * <strong>本番の変換器で読めることまで確かめる。</strong>
     *
     * <p>相手が送る形の JSON を、こちらの Jackson 設定で読む。日時の書き方が
     * 食い違っていると、ここで落ちる。
     */
    @Test
    @DisplayName("本番の変換器で、相手の応答を読める")
    void readsTheProviderResponseWithTheProductionMapper() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            String json = """
                    {
                      "bookingId": "BKG-2026000009",
                      "bookingStatus": "DELIVERED",
                      "shipperId": "1",
                      "shipperName": "丸紅商事株式会社",
                      "shipperType": "CORPORATE",
                      "discountRate": 0.1000,
                      "weightKg": 2500,
                      "cargoType": "GENERAL",
                      "originName": "Tokyo",
                      "destinationName": "Los Angeles",
                      "legCount": 1,
                      "claimedAt": "2027-10-02T00:00:00Z",
                      "misroute": {
                        "at": "2027-09-09T00:00:00Z",
                        "locationUnLocode": "SGSIN",
                        "locationName": "Singapore"
                      },
                      "cancellation": null
                    }
                    """;

            BillableCargoSnapshot snapshot =
                    mapper.readValue(json, BillingSnapshotResponse.class).toSnapshot();

            assertThat(snapshot.bookingId()).isEqualTo("BKG-2026000009");
            assertThat(snapshot.corporate())
                    .as("法人の判定が効いていない。契約割引が入らない")
                    .isTrue();
            assertThat(snapshot.discountRate()).isEqualByComparingTo("0.1000");
            assertThat(snapshot.legCount()).isEqualTo(1);
            assertThat(snapshot.misroute().locationName()).isEqualTo("Singapore");
            assertThat(snapshot.cancellation()).isNull();
        });
    }

    /**
     * <strong>相手が項目を足しても落ちない。</strong>
     *
     * <p>落ちる形にすると、bookingms は項目 1 つ足すのに billingms のデプロイを待つ。
     */
    @Test
    @DisplayName("知らない項目があっても読める")
    void toleratesUnknownFields() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            String json = """
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
                      "somethingAddedLater": "ignored"
                    }
                    """;

            assertThat(mapper.readValue(json, BillingSnapshotResponse.class).toSnapshot()
                    .bookingId()).isEqualTo("BKG-2026000007");
        });
    }

    /**
     * <strong>個人荷主を法人と取り違えない。</strong>
     *
     * <p>取り違えると、契約の無い荷主に割引が入る。
     */
    @Test
    @DisplayName("個人荷主は法人として読まない")
    void doesNotTreatIndividualShippersAsCorporate() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            String json = """
                    {
                      "bookingId": "BKG-2026000008",
                      "bookingStatus": "DELIVERED",
                      "shipperId": "2",
                      "shipperName": "山田太郎",
                      "shipperType": "INDIVIDUAL",
                      "discountRate": null,
                      "weightKg": 800,
                      "cargoType": "REFRIGERATED",
                      "originName": "Tokyo",
                      "destinationName": "Singapore",
                      "legCount": 1,
                      "claimedAt": "2027-09-20T00:00:00Z"
                    }
                    """;

            BillableCargoSnapshot snapshot =
                    mapper.readValue(json, BillingSnapshotResponse.class).toSnapshot();

            assertThat(snapshot.corporate())
                    .as("個人荷主を法人として読んでいる。契約の無い荷主に割引が入る")
                    .isFalse();
            assertThat(snapshot.discountRate()).isNull();
        });
    }

    /**
     * 呼び出す経路と主体は契約が決める。**写して持つと、片方だけ直る。**
     *
     * <p>本番のコードは契約フィクスチャを読めないので、経路そのものは共有できない。
     * <strong>ここで両側を突き合わせる</strong>——リテラルと比べても、契約を直した
     * ときに呼び出し側が追随したかは分からない。
     */
    @Test
    @DisplayName("呼び出す経路と主体は契約から取る")
    void usesThePathAndPrincipalFromTheContract() {
        assertThat(RestBillingSnapshotFinder.SNAPSHOT_PATH)
                .as("引く経路が契約と食い違っている。相手は 404 を返す")
                .isEqualTo(BillingSnapshotContract.PATH);
        assertThat(RestBillingSnapshotFinder.BILLABLE_PATH)
                .as("一覧の経路が契約と食い違っている")
                .isEqualTo(BillingSnapshotContract.UNBILLED_PATH);
        assertThat(RestBillingSnapshotFinder.SYSTEM_PRINCIPAL)
                .as("名乗りが契約と食い違っている。相手のフィルタが一律に断る")
                .isEqualTo(BillingSnapshotContract.CALLER_PRINCIPAL);
    }
}
