package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US24: 航海スケジュールを新規登録する。受け入れ基準に 1:1 で対応させる。 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
class VoyageRegistrationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private VoyageRepository repository;

    @Autowired
    private VoyageQueryService queryService;

    private String voyageNumber;

    @BeforeEach
    void 航海番号を用意する() {
        voyageNumber = "V" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 直行便（大阪 → ロサンゼルス）。 */
    private Map<String, String> form() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("voyageNumber", voyageNumber);
        values.put("vesselName", "さくら丸");
        values.put("carrierName", "日本海運");
        values.put("cargoTypes", "GENERAL");
        values.put("capacityWeightKg", "100000");
        values.put("movements[0].departure", "JPOSA");
        values.put("movements[0].arrival", "USLAX");
        values.put("movements[0].departureTime", "2026-09-01T10:00");
        values.put("movements[0].arrivalTime", "2026-09-14T06:00");
        return values;
    }

    /** 乗り継ぎあり（大阪 → 上海 → ロサンゼルス）。 */
    private Map<String, String> 乗り継ぎフォーム() {
        Map<String, String> values = form();
        values.put("movements[0].arrival", "CNSHA");
        values.put("movements[0].arrivalTime", "2026-09-03T08:00");
        values.put("movements[1].departure", "CNSHA");
        values.put("movements[1].arrival", "USLAX");
        values.put("movements[1].departureTime", "2026-09-04T12:00");
        values.put("movements[1].arrivalTime", "2026-09-16T06:00");
        return values;
    }

    private MockHttpServletRequestBuilder postForm(Map<String, String> values) {
        var req = post("/voyages").with(csrf());
        values.forEach(req::param);
        return req;
    }

    /** 受入基準: 必要項目を入力して登録でき、以後の検索対象になる。 */
    @Test
    void 航海スケジュールを登録できる() throws Exception {
        mockMvc.perform(postForm(form()))
                .andExpect(status().is3xxRedirection());

        var voyage = repository.findByVoyageNumber(new VoyageNumber(voyageNumber)).orElseThrow();
        assertThat(voyage.vesselName().value()).isEqualTo("さくら丸");
        assertThat(voyage.carrierName().value()).isEqualTo("日本海運");
        assertThat(voyage.accepts(RoutingCargoType.GENERAL)).isTrue();
    }

    /** 受入基準: 寄港地を複数かつ順序付きで入力できる。 */
    @Test
    void 寄港地を順序付きで登録できる() throws Exception {
        mockMvc.perform(postForm(乗り継ぎフォーム()))
                .andExpect(status().is3xxRedirection());

        var voyage = repository.findByVoyageNumber(new VoyageNumber(voyageNumber)).orElseThrow();
        assertThat(voyage.origin().unlocode()).isEqualTo("JPOSA");
        assertThat(voyage.destination().unlocode()).isEqualTo("USLAX");
        assertThat(voyage.callingPorts()).singleElement()
                .satisfies(p -> assertThat(p.unlocode()).isEqualTo("CNSHA"));
    }

    /** 受入基準: 必須項目が未入力なら、未入力箇所を明示したエラーが表示される。 */
    @Test
    void 必須項目が未入力なら登録できない() throws Exception {
        Map<String, String> values = new HashMap<>(form());
        values.remove("vesselName");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/form"))
                .andExpect(content().string(Matchers.containsString("船名は必須です")));
    }

    /** 受入基準: 出発日が到着日より後なら、日付の整合性エラーが表示される。 */
    @Test
    void 出発が到着より後の区間は登録できない() throws Exception {
        Map<String, String> values = form();
        values.put("movements[0].departureTime", "2026-09-14T06:00");
        values.put("movements[0].arrivalTime", "2026-09-01T10:00");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("到着時刻は出発時刻より後")));
    }

    /**
     * 受入基準: 同一航海番号は登録できない。
     *
     * <p>**UNIQUE 制約違反を 500 にしない。** 業務の結果として画面に返す。
     */
    @Test
    void 同じ航海番号は登録できない() throws Exception {
        mockMvc.perform(postForm(form())).andExpect(status().is3xxRedirection());

        mockMvc.perform(postForm(form()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("既に登録されています")));
    }

    /** 連結制約は画面ではなくドメインが拒否し、その理由が画面に出る。 */
    @Test
    void つながっていない区間は登録できない() throws Exception {
        Map<String, String> values = 乗り継ぎフォーム();
        // 上海に着いたのに大阪から出発している
        values.put("movements[1].departure", "JPOSA");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("つながっていません")));
    }

    /** 前の区間の到着より前に出発する区間も拒否する。**時間は巻き戻らない。** */
    @Test
    void 前の区間の到着より前に出発する区間は登録できない() throws Exception {
        Map<String, String> values = 乗り継ぎフォーム();
        values.put("movements[1].departureTime", "2026-09-02T12:00");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("前の区間の到着より前")));
    }

    /** 受入基準: 登録後、検索の対象として利用できる（US07 へのつながり）。 */
    @Test
    void 登録した航海が検索できる() throws Exception {
        mockMvc.perform(postForm(form())).andExpect(status().is3xxRedirection());

        var page = queryService.search(
                "JPOSA", "USLAX", null, null, RoutingCargoType.GENERAL, PageRequest.of(1));

        assertThat(page.items())
                .anySatisfy(v -> assertThat(v.voyageNumber()).isEqualTo(voyageNumber));
    }

    /** 何も運べない航海は登録できない。 */
    @Test
    void 貨物種別を選ばないと登録できない() throws Exception {
        Map<String, String> values = new HashMap<>(form());
        values.remove("cargoTypes");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("貨物種別")));
    }

    /**
     * 港マスタに無い港は登録できない。**実在しない港を経由する航海を作らない。**
     *
     * <p><strong>外部キー違反を 500 にしない。</strong> どの港が登録されていないかを
     * 画面に示す。「登録できません」だけでは利用者は直しようがない。
     */
    @Test
    void 港マスタに無い港は登録できない() throws Exception {
        Map<String, String> values = form();
        values.put("movements[0].departure", "ZZZZZ");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/form"))
                .andExpect(content().string(Matchers.containsString("ZZZZZ")))
                .andExpect(content().string(
                        Matchers.containsString("港マスタに登録されていない港")));
    }

    @Test
    void 登録画面が開ける() throws Exception {
        mockMvc.perform(get("/voyages/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("航海スケジュール登録")));
    }
}
